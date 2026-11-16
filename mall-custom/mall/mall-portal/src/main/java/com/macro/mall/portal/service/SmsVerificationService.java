package com.macro.mall.portal.service;

import com.macro.mall.common.config.TencentSmsProperties;
import com.macro.mall.common.util.SmsPrivacy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SmsVerificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LIMIT_SCRIPT = "if redis.call('EXISTS',KEYS[1])==1 then return 1 end;"
            + "if tonumber(redis.call('GET',KEYS[2]) or '0')>=10 then return 2 end;"
            + "if tonumber(redis.call('GET',KEYS[3]) or '0')>=5 then return 3 end;"
            + "if tonumber(redis.call('GET',KEYS[4]) or '0')>=50 then return 3 end;"
            + "redis.call('SET',KEYS[1],'1','EX',60);"
            + "if redis.call('INCR',KEYS[2])==1 then redis.call('EXPIRE',KEYS[2],ARGV[1]) end;"
            + "if redis.call('INCR',KEYS[3])==1 then redis.call('EXPIRE',KEYS[3],60) end;"
            + "if redis.call('INCR',KEYS[4])==1 then redis.call('EXPIRE',KEYS[4],ARGV[1]) end;return 0";
    private final StringRedisTemplate redis;
    private final UmsMemberCacheService cache;
    private final SmsSendService sender;
    private final TencentSmsProperties config;
    private final JdbcTemplate jdbc;
    private final String database;
    private final long lifetime;

    public SmsVerificationService(StringRedisTemplate redis, UmsMemberCacheService cache, SmsSendService sender,
                                  TencentSmsProperties config, JdbcTemplate jdbc,
                                  @Value("${redis.database}") String database,
                                  @Value("${redis.expire.authCode:90}") long lifetime) {
        this.redis = redis;
        this.cache = cache;
        this.sender = sender;
        this.config = config;
        this.jdbc = jdbc;
        this.database = database;
        this.lifetime = lifetime;
    }

    public Map<String, Object> send(String phone, HttpServletRequest request) {
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        checkLimits(phone, clientIp(request));
        String previous = cache.getAuthCode(phone);
        String code;
        do { code = String.format("%06d", RANDOM.nextInt(1000000)); } while (code.equals(previous));
        cache.setAuthCode(phone, code);
        SmsSendService.SmsSendResult result = sender.sendCode(phone, code);
        record(phone, code, result);
        boolean simulation = config.sandboxSimulationAllowed() && "SKIPPED".equals(result.status());
        if (!result.sent() && !simulation) {
            cache.invalidateAuthCode(phone, code);
            throw unavailable();
        }
        if (!cache.approveAuthCode(phone, code, result.sent())) {
            cache.invalidateAuthCode(phone, code);
            throw unavailable();
        }
        return Map.of("expiresIn", lifetime, "resendAfter", 60, "sandboxTest", simulation);
    }

    private void checkLimits(String phone, String ip) {
        Instant now = Instant.now();
        var day = now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        long seconds = Math.max(1, Duration.between(now, day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()).getSeconds());
        String prefix = database + ":sms:";
        String phoneHash = hash(phone);
        String ipHash = hash(ip);
        List<String> keys = List.of(prefix + phoneHash + ":cooldown", prefix + phoneHash + ":" + day,
                prefix + "ip:" + ipHash + ":" + now.getEpochSecond()/60, prefix + "ip:" + ipHash + ":" + day);
        Long limited = redis.execute(new DefaultRedisScript<>(LIMIT_SCRIPT, Long.class), keys, String.valueOf(seconds));
        if (limited == null) throw unavailable();
        if (limited == 1) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请在60秒后重新获取验证码");
        if (limited == 2) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "今日验证码次数已达上限，请明日再试");
        if (limited != 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
    }

    private void record(String phone, String code, SmsSendService.SmsSendResult result) {
        String message = SmsPrivacy.safeMessage(result.errorMessage(), phone, code);
        String providerCode = SmsPrivacy.safeMessage(result.errorCode(), phone, code);
        LOGGER.info("SMS target={} provider={} status={} code={} message={} requestId={}", SmsPrivacy.maskPhone(phone), result.provider(), result.status(), providerCode, message, result.requestId());
        jdbc.update("INSERT INTO sys_message_record(channel,target,content,biz_type,biz_id,status,error_msg,provider,provider_code,provider_message,request_id,serial_no) VALUES('SMS',?,'账号验证码','AUTH_CODE',?,?,?,?,?,?,?,?)",
                SmsPrivacy.maskPhone(phone), UUID.randomUUID().toString(), result.status(), result.sent() ? null : message == null ? null : message.substring(0, Math.min(255, message.length())),
                result.provider(), providerCode, message, result.requestId(), result.serialNo());
    }

    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if ("127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote)) {
            String forwarded = request.getHeader("X-Real-IP");
            if (forwarded != null && forwarded.length()<=45 && forwarded.matches("[0-9a-fA-F:.]+")) return forwarded;
        }
        return remote == null ? "unknown" : remote;
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("无法计算验证码限频键"); }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码暂时无法发送，请稍后重试");
    }
}
