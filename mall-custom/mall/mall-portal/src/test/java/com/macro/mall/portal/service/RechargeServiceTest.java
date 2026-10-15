package com.macro.mall.portal.service;

import com.macro.mall.portal.config.RechargeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RechargeServiceTest {
    JdbcTemplate jdbc;
    RechargeService service;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE ums_recharge_order(order_no VARCHAR(40) PRIMARY KEY,member_id BIGINT,request_id VARCHAR(64),amount_cents BIGINT,channel VARCHAR(10),status VARCHAR(12) DEFAULT 'PENDING',transaction_id VARCHAR(128),balance_after_cents BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,paid_at TIMESTAMP,UNIQUE(member_id,request_id),UNIQUE(channel,transaction_id))");
        service=new RechargeService(jdbc,new RechargeProperties());
    }
    void order(String no,long cents) {jdbc.update("INSERT INTO ums_recharge_order(order_no,member_id,request_id,amount_cents,channel) VALUES(?,1,?,?,'WECHAT')",no,no,cents);}
    void post(String no,String trade,String amount) {tx.executeWithoutResult(s->service.accept(new MembershipPaymentGateway.VerifiedPayment(no,trade,"WECHAT",new BigDecimal(amount))));}
    long balance() {return jdbc.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=1",Long.class);}
    @Test void exactCentsAndInputValidation() {
        assertEquals(1,RechargeService.cents("0.01"));assertEquals(10010,RechargeService.cents("100.10"));
        assertEquals(5000000,RechargeService.cents("50000.00"));
        for(String bad:new String[]{"0","-1","0.001","1e2","NaN","50000.01"," 1","01","1."})
            assertThrows(IllegalArgumentException.class,()->RechargeService.cents(bad));
    }
    @Test void paymentPostsExactlyOnceAndDisplaysTwoDecimals() {
        order("RC1",10010);post("RC1","WX1","100.10");post("RC1","WX1","100.100");
        assertEquals(10010,balance());assertEquals("100.10",service.account(1).get("balance"));
        assertEquals("PAID",service.order(1,"RC1").get("status"));
        assertThrows(Exception.class,()->service.order(2,"RC1"));
    }
    @Test void underpaymentOverpaymentAndWrongChannelNeverCredit() {
        order("RC1",10000);
        for(String bad:new String[]{"99.99","100.01","100.001"}) assertThrows(Exception.class,()->post("RC1","WX1",bad));
        assertThrows(Exception.class,()->tx.executeWithoutResult(s->service.accept(new MembershipPaymentGateway.VerifiedPayment("RC1","ALI1","ALIPAY",new BigDecimal("100")))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ums_recharge_account",Integer.class));
    }
    @Test void reusedTransactionRollsBackAccountAndOrder() {
        order("RC1",10000);order("RC2",10000);post("RC1","WX1","100");
        assertThrows(Exception.class,()->post("RC2","WX1","100"));
        assertEquals(10000,balance());assertEquals("PENDING",service.order(1,"RC2").get("status"));
        assertThrows(Exception.class,()->post("RC1","WX2","100"));
    }
    @Test void concurrentCallbacksAndDifferentOrdersPreserveSum() throws Exception {
        order("RC1",1);order("RC2",10010);
        var pool=Executors.newFixedThreadPool(4);
        try {
            var futures=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<12;i++) {final int n=i%2;futures.add(pool.submit(()->post("RC"+(n+1),"WX"+(n+1),n==0?"0.01":"100.10")));}
            for(var f:futures) f.get(15,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(10011,balance());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ums_recharge_order WHERE status='PAID'",Integer.class));
    }
    @Test void missingCredentialsFailClosed() {
        assertThrows(Exception.class,()->service.create(1,"100","WECHAT","request-1234567890"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ums_recharge_order",Integer.class));
    }
    @Test void createRetriesReuseOrderAndRejectChangedAmountOrChannel() {
        var gateway=org.mockito.Mockito.mock(MembershipPaymentGateway.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"gateway",gateway);
        var first=tx.execute(s->service.create(1,"100.10","WECHAT","request-1234567890"));
        var retry=tx.execute(s->service.create(1,"100.1","WECHAT","request-1234567890"));
        assertEquals(first.get("orderNo"),retry.get("orderNo"));
        assertThrows(Exception.class,()->tx.execute(s->service.create(1,"100.11","WECHAT","request-1234567890")));
        assertThrows(Exception.class,()->tx.execute(s->service.create(1,"100.10","ALIPAY","request-1234567890")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ums_recharge_order",Integer.class));
        assertEquals("0.00",service.account(1).get("balance"));
    }
}
