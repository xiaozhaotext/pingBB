package com.macro.mall.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.macro.mall.config.AlipayChannelProperties;
import com.macro.mall.config.PaymentRefundProperties;
import com.macro.mall.service.PaymentRefundService.RefundOutcome;
import com.macro.mall.service.PaymentRefundService.RefundRequest;
import com.macro.mall.service.impl.AlipayPaymentRefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 支付宝退款：开关、渠道、凭证、金额四道闸门，以及成功/失败两种渠道返回。 */
@ExtendWith(MockitoExtension.class)
class AlipayPaymentRefundServiceImplTest {

    @Mock AlipayClient alipayClient;
    private final AlipayChannelProperties channelProperties = new AlipayChannelProperties();
    private final PaymentRefundProperties refundProperties = new PaymentRefundProperties();
    private final AlipayPaymentRefundServiceImpl service = new AlipayPaymentRefundServiceImpl();

    private static final RefundRequest REQUEST = new RefundRequest(
            "ALIPAY", "SN2026", "T123", new BigDecimal("19.90"), "RA7", "订单退货退款");

    @BeforeEach
    void setUp() {
        channelProperties.setGatewayUrl("https://openapi.alipay.com/gateway.do");
        channelProperties.setAppId("app");
        channelProperties.setAppPrivateKey("private");
        channelProperties.setAlipayPublicKey("public");
        refundProperties.setEnabled(true);
        refundProperties.setChannels(List.of("ALIPAY"));
        ReflectionTestUtils.setField(service, "channelProperties", channelProperties);
        ReflectionTestUtils.setField(service, "refundProperties", refundProperties);
        ReflectionTestUtils.setField(service, "client", alipayClient);
    }

    @Test
    void disabledRefundIsSkippedWithoutChannelCall() {
        refundProperties.setEnabled(false);
        assertEquals(RefundOutcome.State.SKIPPED, service.refund(REQUEST).state());
        verifyNoInteractions(alipayClient);
    }

    @Test
    void unsupportedChannelIsSkipped() {
        RefundRequest wechat = new RefundRequest("WECHAT", "SN", null, BigDecimal.ONE, "RA1", null);
        assertEquals("CHANNEL_NOT_SUPPORTED:WECHAT", service.refund(wechat).errorCode());
        verifyNoInteractions(alipayClient);
    }

    @Test
    void missingCredentialsAreSkipped() {
        channelProperties.setAppId("");
        assertEquals("ALIPAY_CREDENTIALS_MISSING", service.refund(REQUEST).errorCode());
        verifyNoInteractions(alipayClient);
    }

    @Test
    void nonPositiveAmountFailsWithoutChannelCall() {
        RefundRequest zero = new RefundRequest("ALIPAY", "SN", null, BigDecimal.ZERO, "RA1", null);
        RefundOutcome outcome = service.refund(zero);
        assertEquals(RefundOutcome.State.FAILED, outcome.state());
        assertEquals("INVALID_AMOUNT", outcome.errorCode());
        verifyNoInteractions(alipayClient);
    }

    @Test
    void successfulChannelRefundReturnsSuccess() throws AlipayApiException {
        AlipayTradeRefundResponse response = mock(AlipayTradeRefundResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getTradeNo()).thenReturn("T123");
        when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);
        RefundOutcome outcome = service.refund(REQUEST);
        assertEquals(RefundOutcome.State.SUCCESS, outcome.state());
        assertEquals("T123", outcome.channelRefundNo());
    }

    @Test
    void channelRejectionReturnsFailure() throws AlipayApiException {
        AlipayTradeRefundResponse response = mock(AlipayTradeRefundResponse.class);
        when(response.isSuccess()).thenReturn(false);
        when(response.getSubCode()).thenReturn("ACQ.TRADE_NOT_EXIST");
        when(response.getSubMsg()).thenReturn("交易不存在");
        when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);
        RefundOutcome outcome = service.refund(REQUEST);
        assertEquals(RefundOutcome.State.FAILED, outcome.state());
        assertEquals("ACQ.TRADE_NOT_EXIST", outcome.errorCode());
    }

    @Test
    void channelExceptionReturnsFailure() throws AlipayApiException {
        when(alipayClient.execute(any(AlipayTradeRefundRequest.class))).thenThrow(new AlipayApiException("boom"));
        assertEquals(RefundOutcome.State.FAILED, service.refund(REQUEST).state());
    }
}