package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.component.RechargeReconciliation;
import com.macro.mall.portal.config.RechargeProperties;
import com.macro.mall.portal.controller.RechargeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real embedded HTTP server + Spring transactional service + isolated SQL database.
 * Authentication and provider SDK are test doubles; this does not verify merchant credentials. */
@SpringBootTest(classes=RechargeHttpTest.Application.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.main.banner-mode=off","logging.level.root=WARN","spring.autoconfigure.exclude=com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure"})
@org.springframework.test.context.ActiveProfiles("recharge-http-test")
class RechargeHttpTest {
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Profile("recharge-http-test")
    @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class,ManagementWebSecurityAutoConfiguration.class})
    @EnableTransactionManagement
    @Import({RechargeController.class,RechargeService.class,RechargeProperties.class,RechargeReconciliation.class})
    static class Application {
        @Bean DataSource dataSource() {return new DriverManagerDataSource("jdbc:h2:mem:rechargehttp;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");}
        @Bean UmsMemberService members() {return mock(UmsMemberService.class);}
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired RechargeService service;
    @Autowired RechargeProperties properties;
    @Autowired RechargeReconciliation reconciliation;
    @Autowired UmsMemberService members;
    MembershipPaymentGateway gateway;
    @BeforeEach void setup() {
        jdbc.execute("DROP TABLE IF EXISTS ums_recharge_order");
        jdbc.execute("DROP TABLE IF EXISTS ums_recharge_account");
        jdbc.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE ums_recharge_order(order_no VARCHAR(40) PRIMARY KEY,member_id BIGINT,request_id VARCHAR(64),amount_cents BIGINT,channel VARCHAR(10),status VARCHAR(12) DEFAULT 'PENDING',transaction_id VARCHAR(128),balance_after_cents BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,paid_at TIMESTAMP,checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(member_id,request_id),UNIQUE(channel,transaction_id))");
        var member=new UmsMember();member.setId(1L);when(members.getCurrentMember()).thenReturn(member);
        gateway=mock(MembershipPaymentGateway.class);ReflectionTestUtils.setField(service,"gateway",gateway);
        properties.getPayment().setEnabled(true);
    }
    Map create(String request) {
        var response=http.postForEntity("/member/recharge/orders",Map.of("amount","100.10","channel","WECHAT","requestId",request,"memberId",999),Map.class);
        assertEquals(200,response.getStatusCode().value());assertEquals(200,response.getBody().get("code"));
        return (Map)response.getBody().get("data");
    }
    String balance() {return (String)((Map)http.getForObject("/member/recharge/account",Map.class).get("data")).get("balance");}
    @Test void realHttpCreateRetryPayCallbackAndReadBalance() {
        var order=create("http-request-1234567890");var retry=create("http-request-1234567890");
        assertEquals(order.get("orderNo"),retry.get("orderNo"));assertEquals("0.00",balance());
        String no=(String)order.get("orderNo");
        when(gateway.start(eq(no),eq("RECHARGE"),eq(new BigDecimal("100.10")),eq("WECHAT"),eq("WECHAT_H5"),isNull(),anyString()))
                .thenReturn(Map.of("type","WECHAT_H5_URL","h5Url","https://pay.example/"));
        assertEquals(200,http.postForObject("/member/recharge/orders/"+no+"/pay",null,Map.class).get("code"));
        assertEquals("0.00",balance());
        when(gateway.verifyWechat(anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(new MembershipPaymentGateway.VerifiedPayment(no,"WX-http","WECHAT",new BigDecimal("100.10")));
        assertEquals(200,notifyWechat().getStatusCode().value());assertEquals(200,notifyWechat().getStatusCode().value());
        assertEquals("100.10",balance());
        assertEquals(1L,jdbc.queryForObject("SELECT member_id FROM ums_recharge_order WHERE order_no=?",Long.class,no));
        assertNotEquals(200,http.postForObject("/member/recharge/orders/"+no+"/pay",null,Map.class).get("code"));
    }
    org.springframework.http.ResponseEntity<String> notifyWechat() {
        var headers=new org.springframework.http.HttpHeaders();
        for(String header:new String[]{"Serial","Nonce","Signature","Timestamp"}) headers.set("Wechatpay-"+header,"test");
        return http.postForEntity("/member/recharge/notify/wechat",new org.springframework.http.HttpEntity<>("{}",headers),String.class);
    }
    @Test void badSignatureAndWrongAmountNeverChangeBalance() {
        String no=(String)create("http-request-1234567890").get("orderNo");
        when(gateway.verifyWechat(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalArgumentException("invalid signature"));
        assertEquals(500,notifyWechat().getStatusCode().value());assertEquals("0.00",balance());
        doReturn(new MembershipPaymentGateway.VerifiedPayment(no,"WX-http","WECHAT",new BigDecimal("100.09")))
                .when(gateway).verifyWechat(anyString(),anyString(),anyString(),anyString(),anyString());
        assertEquals(500,notifyWechat().getStatusCode().value());assertEquals("0.00",balance());
    }
    @Test void missingCallbackIsRecoveredExactlyOnceByReconciliation() {
        String no=(String)create("http-request-1234567890").get("orderNo");
        jdbc.update("UPDATE ums_recharge_order SET created_at=? WHERE order_no=?",Timestamp.from(Instant.now().minusSeconds(120)),no);
        when(gateway.query(no,"WECHAT")).thenReturn(new MembershipPaymentGateway.VerifiedPayment(no,"WX-query","WECHAT",new BigDecimal("100.10")));
        reconciliation.reconcile();reconciliation.reconcile();
        assertEquals("100.10",balance());verify(gateway,times(1)).query(no,"WECHAT");
    }
    @Test void otherMemberCannotReadOrPayOrder() {
        String no=(String)create("http-request-1234567890").get("orderNo");
        var other=new UmsMember();other.setId(2L);when(members.getCurrentMember()).thenReturn(other);
        assertNotEquals(200,http.getForObject("/member/recharge/orders/"+no,Map.class).get("code"));
        assertNotEquals(200,http.postForObject("/member/recharge/orders/"+no+"/pay",null,Map.class).get("code"));
        verify(gateway,never()).start(anyString(),anyString(),any(),anyString(),anyString(),any(),anyString());
    }
}
