package com.macro.mall.portal.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.WechatOrderPayService.VerifiedPayment;
import com.macro.mall.portal.service.impl.OrderWechatPayHandler;
import com.macro.mall.portal.service.impl.PaymentExceptionRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 微信支付结果入账：金额核对、幂等、迟到支付补偿。 */
@ExtendWith(MockitoExtension.class)
class OrderWechatPayHandlerTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock OmsPortalOrderService portalOrderService;
    @Mock PaymentExceptionRecorder paymentExceptionRecorder;
    @InjectMocks OrderWechatPayHandler handler;

    private static OmsOrder order(long id, String sn, int status, String amount) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setOrderSn(sn);
        order.setStatus(status);
        order.setPayAmount(new BigDecimal(amount));
        return order;
    }

    @Test
    void unknownOrderFails() {
        when(portalOrderService.getOrderByOrderSn("SN1")).thenReturn(null);
        assertEquals("failure", handler.handle(new VerifiedPayment("SN1", "TX1", new BigDecimal("10.00"))));
        verify(paymentExceptionRecorder, never()).record(any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void amountMismatchIsRecordedAndRejected() {
        when(portalOrderService.getOrderByOrderSn("SN1")).thenReturn(order(1L, "SN1", 0, "10.00"));
        assertEquals("failure", handler.handle(new VerifiedPayment("SN1", "TX1", new BigDecimal("9.00"))));
        verify(paymentExceptionRecorder).record(any(), eq("TX1"), any(), eq("AMOUNT_MISMATCH"), eq("WECHAT"));
        verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), any());
    }

    @Test
    void alreadyPaidMatchingPaymentIsIdempotent() {
        when(portalOrderService.getOrderByOrderSn("SN1")).thenReturn(order(1L, "SN1", 1, "10.00"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(1L);
        assertEquals("success", handler.handle(new VerifiedPayment("SN1", "TX1", new BigDecimal("10.00"))));
        verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), any());
    }

    @Test
    void pendingOrderIsMarkedPaidOnce() {
        when(portalOrderService.getOrderByOrderSn("SN1")).thenReturn(order(1L, "SN1", 0, "10.00"));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        assertEquals("success", handler.handle(new VerifiedPayment("SN1", "TX1", new BigDecimal("10.00"))));
        verify(portalOrderService).paySuccessByOrderSn("SN1", 2);
        verify(paymentExceptionRecorder, never()).record(any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void duplicateInsertMatchingConfirmationIsIdempotent() {
        when(portalOrderService.getOrderByOrderSn("SN1")).thenReturn(order(1L, "SN1", 0, "10.00"));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(1L);
        assertEquals("success", handler.handle(new VerifiedPayment("SN1", "TX1", new BigDecimal("10.00"))));
        verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), any());
    }

    @Test
    void latePaymentGoesToManualCompensation() {
        when(portalOrderService.getOrderByOrderSn("SN1")).thenReturn(order(1L, "SN1", 4, "10.00"));
        assertEquals("success", handler.handle(new VerifiedPayment("SN1", "TX1", new BigDecimal("10.00"))));
        verify(paymentExceptionRecorder).record(any(), eq("TX1"), any(),
                eq("LATE_PAYMENT_CLOSED_ORDER"), eq("WECHAT"));
        verify(portalOrderService, never()).paySuccessByOrderSn(anyString(), any());
    }
}