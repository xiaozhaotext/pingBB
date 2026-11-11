package com.macro.mall.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.WechatMiniProgramProperties;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatMiniProgramServiceTest {
    JdbcTemplate jdbc; TransactionTemplate tx; WechatMiniProgramService service;
    final ObjectMapper mapper = new ObjectMapper();
    final Map<String,String> tickets = new HashMap<>();
    String providerBody;
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc = new JdbcTemplate(ds);tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE ums_member_wechat_identity(app_id VARCHAR(32),member_id BIGINT,open_id VARCHAR(128),updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(app_id,member_id))");
        var redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call->{tickets.put(call.getArgument(0),call.getArgument(1));return null;}).when(values).set(anyString(),anyString(),eq(5L),eq(java.util.concurrent.TimeUnit.MINUTES));
        when(values.get(anyString())).thenAnswer(call->tickets.get(call.getArgument(0)));
        var config=new WechatMiniProgramProperties();config.setEnabled(true);config.setAppId("wx-mini");config.setAppSecret("server-only-secret");
        providerBody="{\"openid\":\"openid-A\",\"session_key\":\"server-only-session\"}";
        service=new WechatMiniProgramService(config,jdbc,redis,mapper) {
            @Override protected com.fasterxml.jackson.databind.JsonNode code2Session(String code) {
                try{return mapper.readTree(providerBody);}catch(Exception e){throw new IllegalStateException(e);}
            }
        };
    }
    @Test void bindsExistingMemberAndExposesOnlyMemberScopedTicket() throws Exception {
        var auth=tx.execute(s->service.login(27,"wx-code"));
        assertEquals(27L,jdbc.queryForObject("SELECT member_id FROM ums_member_wechat_identity WHERE app_id='wx-mini' AND open_id='openid-A'",Long.class));
        assertEquals("openid-A",service.paymentOpenId(27,auth.paymentTicket()));
        assertThrows(ResponseStatusException.class,()->service.paymentOpenId(28,auth.paymentTicket()));
        String response=mapper.writeValueAsString(auth);
        assertFalse(response.contains("openid-A"));assertFalse(response.contains("server-only"));
    }
    @Test void sharedWechatPayerKeepsDifferentMemberSessionsIsolated() {
        var first=tx.execute(s->service.login(27,"code1"));
        var second=tx.execute(s->service.login(28,"code2"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ums_member_wechat_identity",Integer.class));
        assertEquals("openid-A",service.paymentOpenId(27,first.paymentTicket()));
        assertEquals("openid-A",service.paymentOpenId(28,second.paymentTicket()));
        assertThrows(ResponseStatusException.class,()->service.paymentOpenId(28,first.paymentTicket()));
    }
    @Test void separateDeviceTicketsDoNotOverwriteTheOriginalPayer() {
        var first=tx.execute(s->service.login(27,"code1"));providerBody="{\"openid\":\"openid-B\"}";
        var second=tx.execute(s->service.login(27,"code2"));
        assertEquals("openid-A",service.paymentOpenId(27,first.paymentTicket()));
        assertEquals("openid-B",service.paymentOpenId(27,second.paymentTicket()));
    }
    @Test void expiredLoginAndTicketsNeverCreateABinding() {
        providerBody="{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        assertThrows(ResponseStatusException.class,()->tx.execute(s->service.login(27,"bad-code")));
        assertThrows(ResponseStatusException.class,()->service.paymentOpenId(27,"unknown-ticket"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ums_member_wechat_identity",Integer.class));
    }
}
