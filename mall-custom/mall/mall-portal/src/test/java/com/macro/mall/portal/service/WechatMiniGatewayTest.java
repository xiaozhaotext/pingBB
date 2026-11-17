package com.macro.mall.portal.service;

import com.macro.mall.portal.config.*;
import com.macro.mall.portal.service.impl.WechatOrderPayServiceImpl;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatMiniGatewayTest {
    final Transaction transaction=mock(Transaction.class,RETURNS_DEEP_STUBS);
    PrepayRequest sent;
    final WechatOrderPayServiceImpl service=new WechatOrderPayServiceImpl() {
        @Override protected PrepayWithRequestPaymentResponse sendJsapiPrepay(PrepayRequest request) {
            sent=request;
            var response=new PrepayWithRequestPaymentResponse();response.setAppId(request.getAppid());response.setTimeStamp("1");response.setNonceStr("nonce");response.setPackageVal("prepay_id=test");response.setSignType("RSA");response.setPaySign("signature");return response;
        }
        @Override protected Transaction parseNotification(RequestParam param){return transaction;}
        @Override protected Transaction queryTransaction(String no){return transaction;}
    };
    @BeforeEach void setup() throws Exception {
        var config=new OrderWechatPayProperties();config.setEnabled(true);config.setAppId("wx-public");config.setMerchantId("1752296458");config.setMerchantSerialNumber("SERIAL");config.setApiV3Key("test");config.setPrivateKeyPath(Files.createTempFile("merchant-test", ".pem").toString());config.setNotifyUrl("https://example.com/notify");
        var mini=new WechatMiniProgramProperties();mini.setEnabled(true);mini.setAppId("wx734dae9c5f5285b6");mini.setNotifyUrl("https://example.com/mini/notify");
        ReflectionTestUtils.setField(service,"properties",config);ReflectionTestUtils.setField(service,"miniProperties",mini);
        when(transaction.getTradeState()).thenReturn(Transaction.TradeStateEnum.SUCCESS);when(transaction.getAppid()).thenReturn(mini.getAppId());when(transaction.getMchid()).thenReturn(config.getMerchantId());when(transaction.getOutTradeNo()).thenReturn("MP-ORDER");when(transaction.getTransactionId()).thenReturn("WX-TX");when(transaction.getAmount().getCurrency()).thenReturn("CNY");when(transaction.getAmount().getTotal()).thenReturn(50);
    }
    @Test void miniPrepayUsesMiniAppIdNativePayerCallbackAndExactCents() {
        var result=service.prepayMiniProgram("MP-ORDER",new BigDecimal("0.50"),"order","mini-openid");
        assertEquals("wx734dae9c5f5285b6",sent.getAppid());assertEquals("1752296458",sent.getMchid());assertEquals("mini-openid",sent.getPayer().getOpenid());assertEquals(50,sent.getAmount().getTotal());assertEquals("https://example.com/mini/notify",sent.getNotifyUrl());assertEquals("WECHAT_MINIPROGRAM",result.get("type"));
    }
    @Test void callbacksAndSignedQueriesRejectAnotherAppOrMerchant() {
        assertEquals(new BigDecimal("0.50"),service.verifyMiniNotify("{}","s","n","sig","1").amount());
        assertEquals(new BigDecimal("0.50"),service.queryMiniPayment("MP-ORDER").payment().amount());
        when(transaction.getAppid()).thenReturn("wx-public");
        assertThrows(ResponseStatusException.class,()->service.verifyMiniNotify("{}","s","n","sig","1"));
        assertThrows(ResponseStatusException.class,()->service.queryMiniPayment("MP-ORDER"));
        when(transaction.getAppid()).thenReturn("wx734dae9c5f5285b6");when(transaction.getMchid()).thenReturn("OTHER");
        assertThrows(ResponseStatusException.class,()->service.verifyMiniNotify("{}","s","n","sig","1"));
    }
}
