package com.macro.mall.portal.service;

import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.config.TencentSmsProperties;
import com.macro.mall.common.util.SmsPrivacy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TencentSmsSender {
    private static final String HOST = "sms.tencentcloudapi.com";
    private final TencentSmsProperties config;
    private final ObjectMapper json;
    private final long lifetimeSeconds;

    public TencentSmsSender(TencentSmsProperties config, ObjectMapper json,
                            @Value("${redis.expire.authCode:90}") long lifetimeSeconds) {
        this.config = config;
        this.json = json;
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public SmsSendService.SmsSendResult send(String phone, String code) {
        if (config.sandboxSimulationAllowed()) return SmsSendService.SmsSendResult.skipped("TENCENT", "沙箱模拟，未发送真实短信");
        if (!config.configured()) return SmsSendService.SmsSendResult.failed("TENCENT", "CONFIGURATION_MISSING", "腾讯云短信环境配置不完整");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("SmsSdkAppId", config.getSdkAppId());
            payload.put("SignName", config.getSignName());
            payload.put("TemplateId", config.getTemplateId());
            // Tencent verification templates only accept numeric digits in parameters.
            // Round down the notice to whole minutes while retaining the Redis deadline.
            String noticeMinutes = String.valueOf(Math.max(1, lifetimeSeconds / 60));
            payload.put("TemplateParamSet", List.of(code, noticeMinutes));
            payload.put("PhoneNumberSet", List.of("+86" + phone));
            String body = json.writeValueAsString(payload);
            long timestamp = Instant.now().getEpochSecond();
            JsonNode response = json.readTree(post(body, signedHeaders(body, timestamp))).path("Response");
            String requestId = response.path("RequestId").asText("");
            JsonNode error = response.path("Error");
            if (!error.isMissingNode() && !error.isNull()) return result(false, error, requestId, "", phone, code);
            JsonNode statuses = response.path("SendStatusSet");
            if (!statuses.isArray() || statuses.size() != 1) return SmsSendService.SmsSendResult.failed("TENCENT", "INVALID_RESPONSE", "腾讯云短信返回格式异常");
            JsonNode status = statuses.get(0);
            return result("Ok".equals(status.path("Code").asText()), status, requestId, status.path("SerialNo").asText(""), phone, code);
        } catch (Exception error) {
            return SmsSendService.SmsSendResult.failed("TENCENT", "CLIENT_ERROR", "短信服务请求异常（" + error.getClass().getSimpleName() + "）");
        }
    }

    private SmsSendService.SmsSendResult result(boolean sent, JsonNode node, String requestId, String serialNo, String phone, String code) {
        return new SmsSendService.SmsSendResult(sent, false, sent ? "SENT" : "FAILED", "TENCENT",
                SmsPrivacy.safeMessage(node.path("Code").asText("UNKNOWN"), phone, code),
                SmsPrivacy.safeMessage(node.path("Message").asText(""), phone, code), requestId, serialNo);
    }

    Map<String, String> signedHeaders(String body, long timestamp) throws Exception {
        String date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(timestamp));
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + HOST + "\nx-tc-action:sendsms\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256(body);
        String scope = date + "/sms/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + scope + "\n" + sha256(canonicalRequest);
        byte[] secretDate = hmac(("TC3" + config.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, "sms");
        byte[] secretSigning = hmac(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "TC3-HMAC-SHA256 Credential=" + config.getSecretId() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Host", HOST);
        headers.put("X-TC-Action", "SendSms");
        headers.put("X-TC-Version", "2021-01-11");
        headers.put("X-TC-Region", config.getRegion());
        headers.put("X-TC-Timestamp", String.valueOf(timestamp));
        return headers;
    }

    protected String post(String body, Map<String, String> headers) {
        try (var response = HttpRequest.post("https://" + HOST).body(body).addHeaders(headers)
                .timeout(config.getTimeoutMillis()).execute()) {
            return response.body();
        }
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static byte[] hmac(byte[] key, String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
    }
}
