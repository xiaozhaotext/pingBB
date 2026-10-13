package com.macro.mall.portal.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.macro.mall.portal.config.SmsProperties;
import com.macro.mall.portal.service.SmsSendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 短信发送实现：支持阿里云短信（RPC 签名）与通用 HTTP 短信接口。
 * 未启用或凭证缺失时绝不谎报"已发送"。
 */
@Slf4j
@Service
public class SmsSendServiceImpl implements SmsSendService {

    private static final String ALIYUN = "ALIYUN";
    private static final String HTTP = "HTTP";

    @Autowired
    private SmsProperties properties;

    @Override
    public SmsSendResult sendCode(String telephone, String code) {
        if (!properties.isEnabled()) {
            return SmsSendResult.skipped(properties.getProvider(), "未配置短信通道，验证码仅在接口返回");
        }
        if (StrUtil.isBlank(telephone) || !telephone.matches("^1\\d{10}$")) {
            return SmsSendResult.failed(properties.getProvider(), "INVALID_PHONE", "手机号格式不正确");
        }
        if (StrUtil.isBlank(code)) {
            return SmsSendResult.failed(properties.getProvider(), "INVALID_CODE", "验证码为空");
        }
        String provider = StrUtil.blankToDefault(properties.getProvider(), ALIYUN);
        try {
            if (ALIYUN.equalsIgnoreCase(provider)) {
                return sendAliyun(telephone, code);
            }
            if (HTTP.equalsIgnoreCase(provider)) {
                return sendGenericHttp(telephone, code);
            }
            return SmsSendResult.failed(provider, "UNKNOWN_PROVIDER", "不支持的短信服务商：" + provider);
        } catch (Exception e) {
            log.error("短信发送异常，手机号：{}，服务商：{}", telephone, provider, e);
            return SmsSendResult.failed(provider, "SMS_EXCEPTION", e.getMessage());
        }
    }

    /** 阿里云短信 SendSms：RPC 风格 + HMAC-SHA1 签名。 */
    private SmsSendResult sendAliyun(String telephone, String code) {
        if (StrUtil.isBlank(properties.getAccessKeyId()) || StrUtil.isBlank(properties.getAccessKeySecret())
                || StrUtil.isBlank(properties.getSignName()) || StrUtil.isBlank(properties.getTemplateCode())) {
            return SmsSendResult.failed(ALIYUN, "SMS_CREDENTIALS_MISSING", "阿里云短信凭证未配置");
        }
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", properties.getAccessKeyId());
        params.put("Action", "SendSms");
        params.put("Format", "JSON");
        params.put("PhoneNumbers", telephone);
        params.put("RegionId", properties.getRegionId());
        params.put("SignName", properties.getSignName());
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("TemplateCode", properties.getTemplateCode());
        params.put("TemplateParam", "{\"code\":\"" + code + "\"}");
        params.put("Timestamp", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
        params.put("Version", "2017-05-25");
        String canonical = canonicalQuery(params);
        String stringToSign = "GET&%2F&" + percentEncode(canonical);
        String signature = Base64.encode(new HMac(HmacAlgorithm.HmacSHA1,
                (properties.getAccessKeySecret() + "&").getBytes(StandardCharsets.UTF_8))
                .digest(stringToSign.getBytes(StandardCharsets.UTF_8)));
        String url = properties.getEndpoint() + "/?Signature=" + percentEncode(signature) + "&" + canonical;
        String body = httpGet(url);
        if (StrUtil.isBlank(body)) {
            return SmsSendResult.failed(ALIYUN, "EMPTY_RESPONSE", "短信服务无响应");
        }
        String resultCode = null;
        try {
            resultCode = JSONUtil.parseObj(body).getStr("Code");
        } catch (Exception ignored) {
            // 响应不是 JSON，按内容判断
        }
        if ("OK".equalsIgnoreCase(resultCode)) {
            return SmsSendResult.sent(properties.isDevReturnCode(), ALIYUN);
        }
        String message = null;
        try {
            message = JSONUtil.parseObj(body).getStr("Message");
        } catch (Exception ignored) {
            message = body;
        }
        return SmsSendResult.failed(ALIYUN, StrUtil.blankToDefault(resultCode, "SEND_FAILED"), message);
    }

    /** 通用 HTTP 短信接口，覆盖云片/253/自建网关等。 */
    private SmsSendResult sendGenericHttp(String telephone, String code) {
        if (StrUtil.isBlank(properties.getGenericUrl()) || StrUtil.isBlank(properties.getGenericParams())) {
            return SmsSendResult.failed(HTTP, "SMS_CREDENTIALS_MISSING", "通用HTTP短信参数未配置");
        }
        String filled = properties.getGenericParams()
                .replace("${phone}", telephone).replace("${code}", code);
        boolean post = "POST".equalsIgnoreCase(properties.getGenericMethod());
        String response;
        if (post) {
            response = httpPost(properties.getGenericUrl(), parseParams(filled));
        } else {
            String separator = properties.getGenericUrl().contains("?") ? "&" : "?";
            response = httpGet(properties.getGenericUrl() + separator + filled);
        }
        String keyword = properties.getGenericSuccessKeyword();
        if (StrUtil.isNotBlank(keyword)) {
            if (response != null && response.contains(keyword)) {
                return SmsSendResult.sent(properties.isDevReturnCode(), HTTP);
            }
            return SmsSendResult.failed(HTTP, "SEND_REJECTED", "短信接口未返回成功标识：" + response);
        }
        if (response == null) {
            return SmsSendResult.failed(HTTP, "EMPTY_RESPONSE", "短信服务无响应");
        }
        return SmsSendResult.sent(properties.isDevReturnCode(), HTTP);
    }

    private static Map<String, String> parseParams(String text) {
        Map<String, String> values = new HashMap<>();
        for (String pair : text.split("&")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                values.put(pair.substring(0, index), pair.substring(index + 1));
            }
        }
        return values;
    }

    private static String canonicalQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        return sb.toString();
    }

    /** 阿里云 RPC 规定的 RFC3986 编码。 */
    private static String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        } catch (Exception e) {
            throw new IllegalStateException("短信参数编码失败", e);
        }
    }

    /** 可覆盖的 HTTP 调用，便于在隔离测试中替换为固定响应。 */
    protected String httpGet(String url) {
        return HttpUtil.createGet(url).timeout(properties.getTimeoutMillis()).execute().body();
    }

    protected String httpPost(String url, Map<String, String> form) {
        Map<String, Object> body = new HashMap<>();
        if (form != null) {
            body.putAll(form);
        }
        return HttpUtil.createPost(url).form(body).timeout(properties.getTimeoutMillis()).execute().body();
    }
}
