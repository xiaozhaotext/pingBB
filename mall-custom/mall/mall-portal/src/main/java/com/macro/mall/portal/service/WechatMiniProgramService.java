package com.macro.mall.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.WechatMiniProgramProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/** Exchanges wx.login codes on the server and binds the authenticated mall member. */
@Service
public class WechatMiniProgramService {
    private static final String TICKET_PREFIX = "mall:wechat-mini:payment:";
    private final WechatMiniProgramProperties properties;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final SecureRandom random = new SecureRandom();

    public record Authorization(String appId, String paymentTicket) {}

    public WechatMiniProgramService(WechatMiniProgramProperties properties, JdbcTemplate jdbc,
                                   StringRedisTemplate redis, ObjectMapper mapper) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.redis = redis;
        this.mapper = mapper;
    }

    @Transactional
    public Authorization login(long memberId, String code) {
        requireReady();
        if (memberId < 1 || code == null || !code.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录凭证无效，请重新授权");
        }
        String openId = parseOpenId(code2Session(code));
        try {
            int updated = jdbc.update("UPDATE ums_member_wechat_identity SET open_id=?,updated_at=CURRENT_TIMESTAMP "
                    + "WHERE app_id=? AND member_id=?", openId, properties.getAppId(), memberId);
            if (updated == 0) {
                jdbc.update("INSERT INTO ums_member_wechat_identity(app_id,member_id,open_id) VALUES(?,?,?)",
                        properties.getAppId(), memberId, openId);
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "微信授权已更新，请重新发起支付");
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // A device-specific ticket freezes the payer identity across concurrent sessions.
        // Neither the code2session session_key nor the AppSecret reaches the client.
        redis.opsForValue().set(TICKET_PREFIX + ticket,
                memberId + ":" + properties.getAppId() + ":" + openId, 5, TimeUnit.MINUTES);
        return new Authorization(properties.getAppId(), ticket);
    }

    public String paymentOpenId(long memberId, String ticket) {
        requireReady();
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{32}")) expired();
        String saved = redis.opsForValue().get(TICKET_PREFIX + ticket);
        if (saved == null) expired();
        String[] parts = saved.split(":", -1);
        if (parts.length != 3 || !String.valueOf(memberId).equals(parts[0])
                || !properties.getAppId().equals(parts[1]) || !parts[2].matches("[A-Za-z0-9_-]{1,128}")) expired();
        return parts[2];
    }

    protected JsonNode code2Session(String code) {
        String uri = "https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getAppSecret()) + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException();
            return mapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信登录暂时不可用，请重试");
        } catch (Exception exception) {
            // HTTP exception messages can contain the URI and its secret query parameter.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信登录暂时不可用，请重试");
        }
    }

    String parseOpenId(JsonNode body) {
        String openId = body == null ? "" : body.path("openid").asText();
        if (body == null || body.path("errcode").asInt(0) != 0 || !openId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录已失效，请重新发起支付");
        }
        return openId;
    }

    private void requireReady() {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getAppId())
                || !StringUtils.hasText(properties.getAppSecret())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "小程序微信登录尚未配置");
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private void expired() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信授权已失效，请重新发起支付"); }
}
