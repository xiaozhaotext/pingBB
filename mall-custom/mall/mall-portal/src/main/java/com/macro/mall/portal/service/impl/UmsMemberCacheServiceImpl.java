package com.macro.mall.portal.service.impl;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.security.annotation.CacheException;
import com.macro.mall.common.config.TencentSmsProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * UmsMemberCacheService实现类
 * Created by macro on 2020/3/14.
 */
@Service
public class UmsMemberCacheServiceImpl implements UmsMemberCacheService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private TencentSmsProperties smsConfig;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.expire.common}")
    private Long REDIS_EXPIRE;
    @Value("${redis.expire.authCode}")
    private Long REDIS_EXPIRE_AUTH_CODE;
    @Value("${redis.key.member}")
    private String REDIS_KEY_MEMBER;
    @Value("${redis.key.authCode}")
    private String REDIS_KEY_AUTH_CODE;

    @Override
    public void delMember(Long memberId) {
        UmsMember umsMember = memberMapper.selectByPrimaryKey(memberId);
        if (umsMember != null) {
            String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + umsMember.getUsername();
            redisService.del(key);
        }
    }

    @Override
    public UmsMember getMember(String username) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + username;
        return (UmsMember) redisService.get(key);
    }

    @Override
    public void setMember(UmsMember member) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + member.getUsername();
        redisService.set(key, member, REDIS_EXPIRE);
    }

    @Override
    public void setAuthCode(String telephone, String authCode) {
        execute("redis.call('SET',KEYS[1],ARGV[1],'EX'," + REDIS_EXPIRE_AUTH_CODE + ");redis.call('DEL',KEYS[2]);return 1", telephone, authCode);
    }

    @Override
    public String getAuthCode(String telephone) {
        String code = (String) redisService.get(authKey(telephone));
        Object approval = redisService.get(authKey(telephone) + ":approved");
        if (code == null || !code.matches("[0-9]{6}")) return null;
        return ("SENT:" + code).equals(approval) || smsConfig.sandboxSimulationAllowed() && ("TEST:" + code).equals(approval) ? code : null;
    }

    @Override
    public boolean approveAuthCode(String telephone, String code, boolean sent) {
        if (!sent && !smsConfig.sandboxSimulationAllowed()) return false;
        return execute("if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end;local ttl=redis.call('PTTL',KEYS[1]);if ttl<=0 then return 0 end;redis.call('SET',KEYS[2],ARGV[2],'PX',ttl);return 1",
                telephone, code, (sent ? "SENT:" : "TEST:") + code) == 1;
    }

    @Override
    public void invalidateAuthCode(String telephone, String code) {
        execute("if redis.call('GET',KEYS[1])==ARGV[1] then redis.call('DEL',KEYS[1],KEYS[2]);return 1 end;return 0", telephone, code);
    }

    @Override
    public boolean claimAuthCode(String telephone, String code) {
        String approval = smsConfig.sandboxSimulationAllowed() ? "TEST:" + code : "SENT:" + code;
        long claimed = execute("if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end;local approved=redis.call('GET',KEYS[2]);if approved~=ARGV[2] and approved~=ARGV[3] then return 0 end;local ttl=redis.call('PTTL',KEYS[1]);if ttl<=0 then return 0 end;redis.call('SET',KEYS[1],ARGV[4],'PX',ttl);return 1",
                telephone, code, "SENT:" + code, approval, "USED:" + code);
        if (claimed != 1) return false;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    finishClaim(telephone, code, status == STATUS_COMMITTED);
                }
            });
        } else finishClaim(telephone, code, true);
        return true;
    }

    private void finishClaim(String telephone, String code, boolean committed) {
        String action = committed ? "redis.call('DEL',KEYS[1],KEYS[2]);return 1"
                : "local ttl=redis.call('PTTL',KEYS[1]);if ttl>0 then redis.call('SET',KEYS[1],ARGV[2],'PX',ttl) end;return 1";
        try {
            execute("if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end;" + action, telephone, "USED:" + code, code);
        } catch (RuntimeException ignored) {
            // A claimed code remains unusable and expires at its original deadline.
        }
    }

    private String authKey(String telephone) {
        return REDIS_DATABASE + ":" + REDIS_KEY_AUTH_CODE + ":" + telephone;
    }

    private long execute(String script, String telephone, Object... arguments) {
        String key = authKey(telephone);
        Long result = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), List.of(key, key + ":approved"), arguments);
        return result == null ? 0 : result;
    }
}
