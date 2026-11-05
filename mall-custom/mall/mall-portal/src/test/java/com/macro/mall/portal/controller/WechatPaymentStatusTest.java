package com.macro.mall.portal.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.macro.mall.model.*;
import com.macro.mall.portal.service.*;
import com.macro.mall.portal.service.impl.OrderWechatPayHandler;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

class WechatPaymentStatusTest {
    final WechatOrderPayController controller = new WechatOrderPayController();
    final OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
    final UmsMemberService members = mock(UmsMemberService.class);
    final WechatOrderPayService wechat = mock(WechatOrderPayService.class);
    final OrderWechatPayHandler handler = mock(OrderWechatPayHandler.class);
    OmsOrder order;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(controller, "portalOrderService", orders);
        ReflectionTestUtils.setField(controller, "memberService", members);
        ReflectionTestUtils.setField(controller, "wechatOrderPayService", wechat);
        ReflectionTestUtils.setField(controller, "orderWechatPayHandler", handler);
        ReflectionTestUtils.setField(controller, "paymentEnabled", true);
        order = new OmsOrder();
        order.setId(10L);
        order.setOrderSn("SN10");
        order.setMemberId(1L);
        order.setStatus(0);
        order.setPayAmount(new BigDecimal("9.90"));
        when(orders.getOrderByOrderSn("SN10")).thenReturn(order);
        var member = new UmsMember();
        member.setId(1L);
        when(members.getCurrentMember()).thenReturn(member);
    }

    @Test
    void anotherMemberCannotQueryOrReconcileAnOrder() {
        order.setMemberId(2L);
        assertThrows(ResponseStatusException.class, () -> controller.status("SN10"));
        verifyNoInteractions(wechat, handler);
    }

    @Test
    void confirmedOrdersDoNotContactProviderOrGrantAgain() {
        order.setStatus(1);
        assertEquals("CONFIRMED", controller.status("SN10").getData().get("state"));
        verifyNoInteractions(wechat, handler);
    }

    @Test
    void lateNotificationIsRecoveredThroughTheExistingIdempotentHandler() {
        var payment =
                new WechatOrderPayService.VerifiedPayment("SN10", "TX10", new BigDecimal("9.90"));
        when(wechat.queryPayment("SN10"))
                .thenReturn(new WechatOrderPayService.PaymentQuery("SUCCESS", payment));
        when(handler.handle(payment))
                .thenAnswer(
                        i -> {
                            order.setStatus(1);
                            return "success";
                        });
        assertEquals("CONFIRMED", controller.status("SN10").getData().get("state"));
        assertEquals("CONFIRMED", controller.status("SN10").getData().get("state"));
        verify(handler, times(1)).handle(payment);
    }

    @Test
    void paymentFailureToFulfillIsNotReportedAsACompletedOrder() {
        var payment =
                new WechatOrderPayService.VerifiedPayment("SN10", "TX10", new BigDecimal("10.00"));
        when(wechat.queryPayment("SN10"))
                .thenReturn(new WechatOrderPayService.PaymentQuery("SUCCESS", payment));
        when(handler.handle(payment)).thenReturn("failure");
        assertEquals("REVIEW", controller.status("SN10").getData().get("state"));
    }

    @Test
    void closedButPaidOrderGoesToReviewInsteadOfAnotherPayment() {
        order.setStatus(4);
        var payment =
                new WechatOrderPayService.VerifiedPayment("SN10", "TX10", new BigDecimal("9.90"));
        when(wechat.queryPayment("SN10"))
                .thenReturn(new WechatOrderPayService.PaymentQuery("SUCCESS", payment));
        when(handler.handle(payment)).thenReturn("success");
        Map<String, Object> result = controller.status("SN10").getData();
        assertEquals("REVIEW", result.get("state"));
        assertEquals(false, result.get("canRetry"));
    }

    @Test
    void providerNotPaidAllowsRetryButPayingDoesNot() {
        when(wechat.queryPayment("SN10"))
                .thenReturn(new WechatOrderPayService.PaymentQuery("NOTPAY", null));
        assertEquals(true, controller.status("SN10").getData().get("canRetry"));
        verifyNoInteractions(handler);
        when(wechat.queryPayment("SN10"))
                .thenReturn(new WechatOrderPayService.PaymentQuery("USERPAYING", null));
        assertEquals(false, controller.status("SN10").getData().get("canRetry"));
    }
}
