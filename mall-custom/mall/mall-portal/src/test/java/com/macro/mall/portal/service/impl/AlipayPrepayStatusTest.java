package com.macro.mall.portal.service.impl;

import com.alipay.api.AlipayClient;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.config.AlipayConfig;
import com.macro.mall.portal.domain.AliPayParam;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayPrepayStatusTest {
    @Mock OmsPortalOrderService orders;
    @Mock UmsMemberService members;
    @Mock AlipayClient client;
    @Mock AlipayConfig config;
    @InjectMocks AlipayServiceImpl service;

    @Test void paidOrderCannotCreateAnotherWapOrPagePayment() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        OmsOrder order = new OmsOrder();
        order.setMemberId(1L);
        order.setStatus(1);
        order.setPayAmount(new BigDecimal("3999.00"));
        when(members.getCurrentMember()).thenReturn(member);
        when(orders.getOrderByOrderSn("ORDER-1")).thenReturn(order);
        AliPayParam request = new AliPayParam();
        request.setOutTradeNo("ORDER-1");
        assertThrows(ApiException.class, () -> service.webPay(request));
        assertThrows(ApiException.class, () -> service.pay(request));
        verifyNoInteractions(client);
    }
}
