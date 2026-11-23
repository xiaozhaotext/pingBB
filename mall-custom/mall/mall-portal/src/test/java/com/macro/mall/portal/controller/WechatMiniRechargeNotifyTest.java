package com.macro.mall.portal.controller;

import com.macro.mall.portal.service.MembershipPaymentGateway;
import com.macro.mall.portal.service.RechargeService;
import com.macro.mall.portal.service.WechatMiniPaymentService;
import com.macro.mall.portal.service.WechatOrderPayService;
import com.macro.mall.portal.service.impl.OrderWechatPayHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WechatMiniRechargeNotifyTest {
    @Test void signedRechargeNotificationCreditsRechargeAndDoesNotTouchProductOrder() {
        var controller=new WechatOrderPayController();
        var gateway=mock(WechatOrderPayService.class);
        var recharge=mock(RechargeService.class);
        var orders=mock(OrderWechatPayHandler.class);
        var miniOrders=mock(WechatMiniPaymentService.class);
        ReflectionTestUtils.setField(controller,"wechatOrderPayService",gateway);
        ReflectionTestUtils.setField(controller,"rechargeService",recharge);
        ReflectionTestUtils.setField(controller,"orderWechatPayHandler",orders);
        ReflectionTestUtils.setField(controller,"miniPayments",miniOrders);
        when(gateway.verifyMiniNotify("signed","serial","nonce","signature","timestamp"))
                .thenReturn(new WechatOrderPayService.VerifiedPayment("RC123","WX123",new BigDecimal("0.01")));

        assertEquals(204,controller.miniNotify("signed","serial","nonce","signature","timestamp").getStatusCode().value());
        verify(recharge).accept(new MembershipPaymentGateway.VerifiedPayment("RC123","WX123","WECHAT",new BigDecimal("0.01")));
        verifyNoInteractions(orders,miniOrders);
    }
}
