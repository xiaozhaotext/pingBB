package com.macro.mall.portal.service;

import com.macro.mall.portal.config.OrderWechatPayProperties;
import com.macro.mall.portal.service.impl.WechatOrderPayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import com.wechat.pay.java.service.payments.model.Transaction;

/** 微信普通订单支付：未启用/凭证缺失必须 503，参数非法必须 400，绝不发起伪造请求。 */
class WechatOrderPayServiceImplTest {

    private final OrderWechatPayProperties properties = new OrderWechatPayProperties();
    private final WechatOrderPayServiceImpl service = new WechatOrderPayServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    private void enableChannel() throws Exception {
        Path key = Files.createTempFile("wechat-key", ".pem");
        key.toFile().deleteOnExit();
        properties.setEnabled(true);
        properties.setAppId("wx-app");
        properties.setMerchantId("1900000000");
        properties.setMerchantSerialNumber("SERIAL");
        properties.setPrivateKeyPath(key.toString());
        properties.setApiV3Key("api-v3-key");
        properties.setNotifyUrl("https://example.com/wechatPay/notify");
    }

    @Test
    void disabledChannelReturns503() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.prepayH5("SN1", new BigDecimal("10.00"), "订单", "1.2.3.4"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void missingCredentialsReturns503() {
        properties.setEnabled(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.prepayH5("SN1", new BigDecimal("10.00"), "订单", "1.2.3.4"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void blankClientIpReturns400() throws Exception {
        enableChannel();
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.prepayH5("SN1", new BigDecimal("10.00"), "订单", " "));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void nonPositiveAmountReturns400() throws Exception {
        enableChannel();
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.prepayH5("SN1", BigDecimal.ZERO, "订单", "1.2.3.4"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void jsapiWithoutOpenIdReturns400() throws Exception {
        enableChannel();
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.prepayJsapi("SN1", new BigDecimal("10.00"), "订单", null));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void notifyWithoutCredentialsReturns503() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.verifyNotify("{}", "s", "n", "sig", "1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test void queryChecksProviderIdentityCurrencyAndAmountBeforeCreatingPaymentEvidence() throws Exception {
        enableChannel();
        Transaction transaction=mock(Transaction.class,RETURNS_DEEP_STUBS);
        WechatOrderPayServiceImpl queryService=new WechatOrderPayServiceImpl() {
            @Override protected Transaction queryTransaction(String no){return transaction;}
        };
        ReflectionTestUtils.setField(queryService,"properties",properties);
        when(transaction.getOutTradeNo()).thenReturn("SN1");when(transaction.getAppid()).thenReturn("wx-app");
        when(transaction.getMchid()).thenReturn("1900000000");when(transaction.getTradeState()).thenReturn(Transaction.TradeStateEnum.SUCCESS);
        when(transaction.getAmount().getTotal()).thenReturn(990);when(transaction.getAmount().getCurrency()).thenReturn("CNY");
        when(transaction.getTransactionId()).thenReturn("TX1");
        assertEquals(new BigDecimal("9.90"),queryService.queryPayment("SN1").payment().amount());
        when(transaction.getAmount().getCurrency()).thenReturn("USD");
        assertThrows(ResponseStatusException.class,()->queryService.queryPayment("SN1"));
        when(transaction.getAmount().getCurrency()).thenReturn("CNY");when(transaction.getMchid()).thenReturn("other-merchant");
        assertThrows(ResponseStatusException.class,()->queryService.queryPayment("SN1"));
    }
}
