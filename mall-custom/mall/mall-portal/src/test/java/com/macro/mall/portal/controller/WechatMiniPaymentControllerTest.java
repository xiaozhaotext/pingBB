package com.macro.mall.portal.controller;

import com.macro.mall.model.*;
import com.macro.mall.portal.service.*;
import com.macro.mall.portal.service.impl.OrderWechatPayHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatMiniPaymentControllerTest {
    final WechatOrderPayController controller=new WechatOrderPayController();
    final UmsMemberService members=mock(UmsMemberService.class);
    final OmsPortalOrderService orders=mock(OmsPortalOrderService.class);
    final WechatMiniProgramService identity=mock(WechatMiniProgramService.class);
    final WechatMiniPaymentService mini=mock(WechatMiniPaymentService.class);
    final WechatOrderPayService gateway=mock(WechatOrderPayService.class);
    final OrderWechatPayHandler handler=mock(OrderWechatPayHandler.class);
    OmsOrder order;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(controller,"memberService",members);ReflectionTestUtils.setField(controller,"portalOrderService",orders);ReflectionTestUtils.setField(controller,"miniProgram",identity);ReflectionTestUtils.setField(controller,"miniPayments",mini);ReflectionTestUtils.setField(controller,"wechatOrderPayService",gateway);ReflectionTestUtils.setField(controller,"orderWechatPayHandler",handler);ReflectionTestUtils.setField(controller,"paymentEnabled",true);
        var member=new UmsMember();member.setId(42L);when(members.getCurrentMember()).thenReturn(member);
        order=new OmsOrder();order.setId(10L);order.setOrderSn("SN10");order.setMemberId(42L);order.setStatus(0);order.setPayAmount(new BigDecimal("0.50"));when(orders.getOrderByOrderSn("SN10")).thenReturn(order);
    }
    @Test void anotherMembersOrderNeverConsumesAnAuthorizationOrStartsPayment() {
        order.setMemberId(43L);assertThrows(ResponseStatusException.class,()->controller.mini(new WechatOrderPayController.MiniPrepayRequest("SN10","ticket")));verifyNoInteractions(identity,mini,gateway);
    }
    @Test void providerQueryTranslatesAndUsesTheExistingVerifiedPaymentHandler() {
        var paid=new WechatOrderPayService.VerifiedPayment("SN10","WX-TX",new BigDecimal("0.50"));when(mini.query(order)).thenReturn(new WechatOrderPayService.PaymentQuery("SUCCESS",paid));when(handler.handle(paid)).thenAnswer(call->{order.setStatus(1);return "success";});
        assertEquals("CONFIRMED",controller.miniStatus("SN10").getData().get("state"));assertEquals("CONFIRMED",controller.miniStatus("SN10").getData().get("state"));verify(handler,times(1)).handle(paid);verifyNoInteractions(gateway);
    }
    @Test void onlySuccessfullyProcessedSignedNotificationsAreAcknowledged() {
        var provider=new WechatOrderPayService.VerifiedPayment("MP10","TX",new BigDecimal("0.50"));var original=new WechatOrderPayService.VerifiedPayment("SN10","TX",new BigDecimal("0.50"));when(gateway.verifyMiniNotify("{}","s","n","signature","1")).thenReturn(provider);when(mini.originalPayment(provider)).thenReturn(original);when(handler.handle(original)).thenReturn("failure","success");
        var failure=assertThrows(ResponseStatusException.class,()->controller.miniNotify("{}","s","n","signature","1"));assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,failure.getStatusCode());assertEquals(HttpStatus.NO_CONTENT,controller.miniNotify("{}","s","n","signature","1").getStatusCode());
    }
}
