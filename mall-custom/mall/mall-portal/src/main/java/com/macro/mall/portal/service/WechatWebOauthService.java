package com.macro.mall.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.MembershipPaymentProperties;
import com.macro.mall.portal.config.OrderWechatPayProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Server-side OpenID acquisition for WeChat browser JSAPI payments. */
@Service
public class WechatWebOauthService {
    private static final String STATE_PREFIX = "mall:wechat-oauth:state:";
    private static final String OPENID_PREFIX = "mall:wechat-oauth:openid:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final OrderWechatPayProperties orderWechat;
    private final MembershipPaymentProperties membership;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${mall.payment.wechat.oauth.app-secret:}") private String appSecret;
    @Value("${mall.payment.wechat.oauth.callback-url:}") private String callbackUrl;
    @Value("${mall.payment.wechat.oauth.site-url:}") private String siteUrl;

    public WechatWebOauthService(StringRedisTemplate redis, ObjectMapper mapper,
                                 OrderWechatPayProperties orderWechat, MembershipPaymentProperties membership) {
        this.redis = redis;
        this.mapper = mapper;
        this.orderWechat = orderWechat;
        this.membership = membership;
    }

    public String authorize(long memberId, String purpose, Long orderId) {
        String appId = appIdFor(purpose);
        requireConfigured(appId);
        if ("ORDER".equals(purpose) && (orderId == null || orderId <= 0)) badRequest();
        if ("MEMBERSHIP".equals(purpose) && orderId != null) badRequest();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(STATE_PREFIX + state,
                memberId + ":" + purpose + ":" + (orderId == null ? 0 : orderId) + ":" + appId,
                5, TimeUnit.MINUTES);
        return "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + encode(appId)
                + "&redirect_uri=" + encode(callbackUrl) + "&response_type=code&scope=snsapi_base&state="
                + encode(state) + "#wechat_redirect";
    }

    public String callback(String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)
                || !state.matches("[A-Za-z0-9_-]{32}")) badRequest();
        String saved = redis.opsForValue().getAndDelete(STATE_PREFIX + state);
        if (saved == null) badRequest();
        String[] parts = saved.split(":", -1);
        if (parts.length != 4) badRequest();
        String appId = appIdFor(parts[1]);
        if (!appId.equals(parts[3])) badRequest();
        requireConfigured(appId);
        String endpoint = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + encode(appId)
                + "&secret=" + encode(appSecret) + "&code=" + encode(code) + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("WeChat OAuth HTTP failure");
            JsonNode body = mapper.readTree(response.body());
            String openId = body.path("openid").asText();
            if (!appId.equals(body.path("appid").asText(appId)) || !openId.matches("[A-Za-z0-9_-]{1,128}"))
                throw new IllegalStateException("WeChat OAuth response invalid");
            redis.opsForValue().set(openIdKey(Long.parseLong(parts[0]), appId), openId, 30, TimeUnit.DAYS);
            String path = "ORDER".equals(parts[1]) ? "/#/pages/money/pay?orderId=" + Long.parseLong(parts[2])
                    : "/#/pages/user/user";
            return siteUrl.replaceAll("/$", "") + path;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信网页授权失败，请重试");
        }
    }

    public String openId(long memberId, String purpose) {
        String appId = appIdFor(purpose);
        String openId = redis.opsForValue().get(openIdKey(memberId, appId));
        if (!StringUtils.hasText(openId)) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "请先完成微信网页授权");
        }
        return openId;
    }

    public boolean authorized(long memberId, String purpose) {
        return StringUtils.hasText(redis.opsForValue().get(openIdKey(memberId, appIdFor(purpose))));
    }

    public boolean readyFor(String purpose) {
        try {
            requireConfigured(appIdFor(purpose));
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    private String appIdFor(String purpose) {
        if ("ORDER".equals(purpose) && orderWechat.isEnabled()) return orderWechat.getAppId();
        if ("MEMBERSHIP".equals(purpose) && membership.isEnabled() && membership.getWechat().isEnabled())
            return membership.getWechat().getAppId();
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付未启用");
    }

    private void requireConfigured(String appId) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)
                || !StringUtils.hasText(callbackUrl) || !callbackUrl.startsWith("https://")
                || !StringUtils.hasText(siteUrl) || !siteUrl.startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信网页授权未配置");
    }

    private String openIdKey(long memberId, String appId) { return OPENID_PREFIX + appId + ":" + memberId; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private void badRequest() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信授权请求无效"); }
}
