package com.macro.mall.portal.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.config.WechatMiniProgramProperties;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatMiniPaymentServiceTest {
    JdbcTemplate jdbc; TransactionTemplate tx; WechatMiniPaymentService service;
    WechatOrderPayService gateway; OmsOrder order;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE oms_order(id BIGINT PRIMARY KEY,status INT,pay_amount DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE oms_wechat_mini_payment(order_id BIGINT PRIMARY KEY,order_sn VARCHAR(64) UNIQUE,out_trade_no VARCHAR(32) UNIQUE,app_id VARCHAR(32),open_id VARCHAR(128),amount DECIMAL(18,2))");
        jdbc.update("INSERT INTO oms_order VALUES(10,0,0.50)");
        var config=new WechatMiniProgramProperties();config.setEnabled(true);config.setAppId("wx-mini");
        gateway=mock(WechatOrderPayService.class);when(gateway.prepayMiniProgram(anyString(),any(),anyString(),anyString())).thenAnswer(call->Map.of("outTradeNo",call.getArgument(0)));
        service=new WechatMiniPaymentService(jdbc,config,gateway);order=new OmsOrder();order.setId(10L);order.setOrderSn("a-long-existing-web-order-number-with-more-than-32-characters");order.setPayAmount(new BigDecimal("0.50"));
    }
    @Test void reusesAStableProviderAliasAndOriginalServerAmount() {
        var first=tx.execute(s->service.prepay(order,"payer-A"));var retry=tx.execute(s->service.prepay(order,"payer-A"));
        assertEquals(first,retry);assertEquals(32,first.get("outTradeNo").length());assertNotEquals(order.getOrderSn(),first.get("outTradeNo"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oms_wechat_mini_payment",Integer.class));
        verify(gateway,times(2)).prepayMiniProgram(first.get("outTradeNo"),new BigDecimal("0.50"),"商城订单 "+order.getOrderSn(),"payer-A");
    }
    @Test void callbackAndQueryTranslateBackToTheExistingMallOrder() {
        String no=tx.execute(s->service.prepay(order,"payer-A")).get("outTradeNo");
        var paid=new WechatOrderPayService.VerifiedPayment(no,"WX-TX",new BigDecimal("0.50"));
        when(gateway.queryMiniPayment(no)).thenReturn(new WechatOrderPayService.PaymentQuery("SUCCESS",paid));
        assertEquals(order.getOrderSn(),service.originalPayment(paid).outTradeNo());
        assertEquals(order.getOrderSn(),service.query(order).payment().outTradeNo());
        assertThrows(ResponseStatusException.class,()->service.originalPayment(new WechatOrderPayService.VerifiedPayment(no,"WX-TX",new BigDecimal("0.49"))));
        assertThrows(ResponseStatusException.class,()->service.originalPayment(new WechatOrderPayService.VerifiedPayment("UNKNOWN","WX-TX",new BigDecimal("0.50"))));
    }
    @Test void changedAmountOrPayerAndClosedOrdersCannotStartPayment() {
        tx.execute(s->service.prepay(order,"payer-A"));
        assertThrows(ResponseStatusException.class,()->tx.execute(s->service.prepay(order,"payer-B")));
        jdbc.update("UPDATE oms_order SET pay_amount=1.00 WHERE id=10");
        assertThrows(ResponseStatusException.class,()->tx.execute(s->service.prepay(order,"payer-A")));
        jdbc.update("UPDATE oms_order SET status=4 WHERE id=10");
        assertThrows(ResponseStatusException.class,()->tx.execute(s->service.prepay(order,"payer-A")));
        verify(gateway,times(1)).prepayMiniProgram(anyString(),any(),anyString(),anyString());
    }
    @Test void gatewayFailureRollsBackTheAttemptAndAllowsAConsistentRetry() {
        when(gateway.prepayMiniProgram(anyString(),any(),anyString(),anyString())).thenThrow(new IllegalStateException("provider timeout"));
        assertThrows(IllegalStateException.class,()->tx.execute(s->service.prepay(order,"payer-A")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oms_wechat_mini_payment",Integer.class));
    }
}
