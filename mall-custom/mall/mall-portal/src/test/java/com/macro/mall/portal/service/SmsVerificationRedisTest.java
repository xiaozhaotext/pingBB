package com.macro.mall.portal.service;

import com.macro.mall.common.config.BaseRedisConfig;
import com.macro.mall.common.config.TencentSmsProperties;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.service.impl.RedisServiceImpl;
import com.macro.mall.mapper.UmsMemberLevelMapper;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.impl.UmsMemberCacheServiceImpl;
import com.macro.mall.portal.service.impl.UmsMemberServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="sms.redis.port", matches="[0-9]+")
class SmsVerificationRedisTest {
    private static final String PHONE = "13800000000";
    private LettuceConnectionFactory factory;
    private StringRedisTemplate strings;
    private RedisTemplate<String,Object> template;
    private UmsMemberCacheServiceImpl cache;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private SmsVerificationService service;
    private UmsMemberServiceImpl members;
    private final AtomicReference<String> lastCode = new AtomicReference<>();
    private final AtomicInteger sends = new AtomicInteger();
    private String namespace;

    @BeforeEach void setup() {
        namespace = "sms-test-" + UUID.randomUUID();
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("sms.redis.port")));
        factory.afterPropertiesSet();
        factory.start();
        strings = new StringRedisTemplate(factory);
        var redisConfig = new BaseRedisConfig();
        template = redisConfig.redisTemplate(factory, redisConfig.redisSerializer());
        var redis = new RedisServiceImpl();
        ReflectionTestUtils.setField(redis, "redisTemplate", template);
        var config = new TencentSmsProperties(new MockEnvironment());
        cache = new UmsMemberCacheServiceImpl();
        ReflectionTestUtils.setField(cache, "redisService", redis);
        ReflectionTestUtils.setField(cache, "redisTemplate", template);
        ReflectionTestUtils.setField(cache, "smsConfig", config);
        ReflectionTestUtils.setField(cache, "REDIS_DATABASE", namespace);
        ReflectionTestUtils.setField(cache, "REDIS_KEY_AUTH_CODE", "ums:authCode");
        ReflectionTestUtils.setField(cache, "REDIS_EXPIRE_AUTH_CODE", 90L);
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:" + namespace + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(datasource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        jdbc.execute("CREATE TABLE sys_message_record(channel VARCHAR(20),target VARCHAR(100),content VARCHAR(500),biz_type VARCHAR(40),biz_id VARCHAR(100),status VARCHAR(20),error_msg VARCHAR(500),provider VARCHAR(30),provider_code VARCHAR(120),provider_message VARCHAR(500),request_id VARCHAR(100),serial_no VARCHAR(100),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE ums_member(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(64) UNIQUE,phone VARCHAR(20) UNIQUE)");
        SmsSendService sender = (phone,code) -> {
            sends.incrementAndGet();
            lastCode.set(code);
            return new SmsSendService.SmsSendResult(true,false,"SENT","TENCENT","Ok","accepted","request-test","serial-test");
        };
        service = new SmsVerificationService(strings,cache,sender,config,jdbc,namespace,90);
        var mapper = mock(UmsMemberMapper.class);
        when(mapper.selectByExample(any())).thenReturn(List.of());
        doAnswer(call -> {
            UmsMember member = call.getArgument(0);
            return jdbc.update("INSERT INTO ums_member(username,phone) VALUES(?,?)",member.getUsername(),member.getPhone());
        }).when(mapper).insert(any());
        var levels = mock(UmsMemberLevelMapper.class);
        when(levels.selectByExample(any())).thenReturn(List.of());
        members = new UmsMemberServiceImpl();
        ReflectionTestUtils.setField(members,"memberCacheService",cache);
        ReflectionTestUtils.setField(members,"memberMapper",mapper);
        ReflectionTestUtils.setField(members,"memberLevelMapper",levels);
        ReflectionTestUtils.setField(members,"passwordEncoder",new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(members,"jdbcTemplate",jdbc);
    }

    @AfterEach void cleanup() {
        var keys = strings.keys(namespace + ":*");
        if (keys != null && !keys.isEmpty()) strings.delete(keys);
        factory.destroy();
    }

    private MockHttpServletRequest request(String ip) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private void clearCooldown() {
        var keys = strings.keys(namespace + ":sms:*:cooldown");
        if (keys != null) strings.delete(keys);
    }

    @Test void successfulDeliveryDoesNotEchoAndCodeIsSingleUseAfterRegistration() {
        var response = service.send(PHONE,request("192.0.2.1"));
        assertFalse(response.toString().contains(lastCode.get()));
        assertEquals(90L,response.get("expiresIn"));
        assertEquals("138****0000",jdbc.queryForObject("SELECT target FROM sys_message_record",String.class));
        assertEquals("Ok",jdbc.queryForObject("SELECT provider_code FROM sys_message_record",String.class));
        assertThrows(ApiException.class,()->tx.executeWithoutResult(s->members.register("wrong","test-password",PHONE,"999999")));
        tx.executeWithoutResult(s->members.register("registered","test-password",PHONE,lastCode.get()));
        assertNull(cache.getAuthCode(PHONE));
        assertThrows(ApiException.class,()->tx.executeWithoutResult(s->members.register("replay","test-password",PHONE,lastCode.get())));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ums_member",Integer.class));
    }

    @Test void resendInvalidatesThePreviousCodeAndEnforcesSixtySeconds() {
        service.send(PHONE,request("192.0.2.1"));
        String old = lastCode.get();
        var limited = assertThrows(ResponseStatusException.class,()->service.send(PHONE,request("192.0.2.1")));
        assertEquals(429,limited.getStatusCode().value());
        assertEquals(1,sends.get());
        clearCooldown();
        service.send(PHONE,request("192.0.2.1"));
        assertNotEquals(old,lastCode.get());
        assertThrows(ApiException.class,()->members.register("old-code","test-password",PHONE,old));
        assertEquals(lastCode.get(),cache.getAuthCode(PHONE));
    }

    @Test void unsentAndExpiredCodesAreRejected() throws Exception {
        cache.setAuthCode(PHONE,"123456");
        assertNull(cache.getAuthCode(PHONE));
        cache.approveAuthCode(PHONE,"123456",true);
        String key = namespace + ":ums:authCode:" + PHONE;
        assertTrue(template.getExpire(key)>=88 && template.getExpire(key)<=90);
        template.expire(key,java.time.Duration.ofMillis(100));
        Thread.sleep(150);
        assertNull(cache.getAuthCode(PHONE));
        assertThrows(ApiException.class,()->members.register("expired","test-password",PHONE,"123456"));
    }

    @Test void registrationRollbackRestoresOnlyTheSameUnexpiredCode() {
        service.send(PHONE,request("192.0.2.1"));
        String code = lastCode.get();
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{
            members.register("rollback","test-password",PHONE,code);
            throw new IllegalStateException("test rollback");
        }));
        assertEquals(code,cache.getAuthCode(PHONE));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ums_member",Integer.class));
    }

    @Test void concurrentRequestsCannotSendTwice() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0,8).<Callable<Boolean>>mapToObj(i->()->{
                try {service.send(PHONE,request("192.0.2.1"));return true;}
                catch (ResponseStatusException limited) {return false;}
            }).toList();
            long successes=0;
            for(var result:executor.invokeAll(tasks))if(result.get())successes++;
            assertEquals(1,successes);
            assertEquals(1,sends.get());
        } finally {executor.shutdownNow();}
    }

    @Test void phoneDailyLimitAndIpMinuteLimitAreEnforced() {
        for(int i=0;i<10;i++) {
            clearCooldown();
            service.send(PHONE,request("192.0.2."+(i+1)));
        }
        clearCooldown();
        assertEquals(429,assertThrows(ResponseStatusException.class,()->service.send(PHONE,request("192.0.2.20"))).getStatusCode().value());
        for(int i=1;i<=5;i++) service.send("1380000000"+i,request("192.0.2.100"));
        assertEquals(429,assertThrows(ResponseStatusException.class,()->service.send("13800000006",request("192.0.2.100"))).getStatusCode().value());
        assertEquals(15,sends.get());
    }

    @Test void failedDeliveryInvalidatesCodeAndKeepsProviderErrorOutOfResponse() {
        SmsSendService failure = (phone,code)->SmsSendService.SmsSendResult.failed("TENCENT","FailedOperation.TestRejected","phone " + phone + " code " + code + " rejected");
        var failed = new SmsVerificationService(strings,cache,failure,new TencentSmsProperties(new MockEnvironment()),jdbc,namespace,90);
        var error = assertThrows(ResponseStatusException.class,()->failed.send(PHONE,request("192.0.2.1")));
        assertEquals(503,error.getStatusCode().value());
        assertFalse(error.getReason().contains("TestRejected"));
        assertNull(cache.getAuthCode(PHONE));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM sys_message_record",String.class));
        assertEquals("FailedOperation.TestRejected",jdbc.queryForObject("SELECT provider_code FROM sys_message_record",String.class));
        assertFalse(jdbc.queryForObject("SELECT provider_message FROM sys_message_record",String.class).contains(PHONE));
    }

    @Test void forwardedIpIsTrustedOnlyFromTheLocalReverseProxy() {
        var direct = request("192.0.2.2");
        direct.addHeader("X-Real-IP","198.51.100.3");
        assertEquals("192.0.2.2",SmsVerificationService.clientIp(direct));
        var proxied = request("127.0.0.1");
        proxied.addHeader("X-Real-IP","198.51.100.3");
        assertEquals("198.51.100.3",SmsVerificationService.clientIp(proxied));
    }

    @Test
    @EnabledIfSystemProperty(named="sms.test.fullTtl",matches="true")
    void codeExpiresAfterTheFullNinetySecondLifetime() throws Exception {
        service.send(PHONE,request("192.0.2.1"));
        String code = lastCode.get();
        assertEquals(code,cache.getAuthCode(PHONE));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(90500);
        while (System.nanoTime() < deadline) Thread.sleep(1000);
        assertNull(cache.getAuthCode(PHONE));
        assertThrows(ApiException.class,()->members.register("full-expiry","test-password",PHONE,code));
    }
}
