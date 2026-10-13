package com.macro.mall.service;

import com.macro.mall.service.PaymentRefundService.RefundOutcome;
import com.macro.mall.service.PaymentRefundService.RefundRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 退货退款编排：无支付记录不得调渠道，退款结果必须如实登记。 */
@ExtendWith(MockitoExtension.class)
class OrderReturnRefundServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock PaymentRefundService paymentRefundService;
    @InjectMocks OrderReturnRefundService service;

    private static final BigDecimal AMOUNT = new BigDecimal("19.90");

    @Test
    void missingParametersNeverReachChannel() {
        String note = service.refundForReturn(null, 1L, AMOUNT, "退货");
        assertTrue(note.contains("人工核对"));
        verifyNoInteractions(jdbc, paymentRefundService);
    }

    @Test
    void withoutPaymentConfirmationRecordsSkipped() {
        when(jdbc.queryForMap(anyString(), any())).thenThrow(new EmptyResultDataAccessException(1));
        String note = service.refundForReturn(7L, 12L, AMOUNT, "退货");
        assertTrue(note.contains("未查到渠道支付记录"));
        verifyNoInteractions(paymentRefundService);
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), eq("SKIPPED"), any(), any());
    }

    @Test
    void successfulRefundIsRecordedWithChannelOutcome() {
        when(jdbc.queryForMap(anyString(), any())).thenReturn(
                Map.of("channel_code", "ALIPAY", "channel_trade_no", "T123"));
        when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenReturn("SN2026");
        when(paymentRefundService.refund(any())).thenReturn(RefundOutcome.success("T123"));
        String note = service.refundForReturn(7L, 12L, AMOUNT, "退货");
        assertTrue(note.contains("渠道退款已受理"));
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentRefundService).refund(captor.capture());
        assertEquals("ALIPAY", captor.getValue().channelCode());
        assertEquals("SN2026", captor.getValue().outTradeNo());
        assertEquals("RA7", captor.getValue().outRequestNo());
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), eq("SUCCESS"), eq("T123"), any());
    }

    @Test
    void failedRefundIsRecordedHonestly() {
        when(jdbc.queryForMap(anyString(), any())).thenReturn(
                Map.of("channel_code", "ALIPAY", "channel_trade_no", "T123"));
        when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenReturn("SN2026");
        when(paymentRefundService.refund(any()))
                .thenReturn(RefundOutcome.failed("ACQ.TRADE_NOT_EXIST", "交易不存在"));
        String note = service.refundForReturn(7L, 12L, AMOUNT, "退货");
        assertTrue(note.contains("渠道退款失败"));
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), eq("FAILED"), any(), any());
    }

    @Test
    void disabledChannelIsRecordedAsPendingManualRefund() {
        when(jdbc.queryForMap(anyString(), any())).thenReturn(
                Map.of("channel_code", "ALIPAY", "channel_trade_no", "T123"));
        when(jdbc.queryForObject(anyString(), eq(String.class), any())).thenReturn("SN2026");
        when(paymentRefundService.refund(any())).thenReturn(RefundOutcome.skipped("REFUND_CHANNEL_DISABLED"));
        String note = service.refundForReturn(7L, 12L, AMOUNT, "退货");
        assertTrue(note.contains("待人工退款"));
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), eq("SKIPPED"), any(), any());
    }
}