package com.macro.mall.portal.controller;

import com.macro.mall.model.OmsOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WechatOneYuanSandboxGateTest {
    @Test void oneYuanExceptionRequiresSandboxDatabaseAndExactCashTotal() {
        OmsOrder order = new OmsOrder();
        order.setTotalAmount(new BigDecimal("1.00"));
        order.setPayAmount(new BigDecimal("1.00"));
        order.setBalanceAmount(BigDecimal.ZERO);
        assertTrue(WechatOrderPayController.isOneYuanSandboxOrder(order, "mall_demo_sandbox", true));
        assertFalse(WechatOrderPayController.isOneYuanSandboxOrder(order, "mall_demo", true));
        assertFalse(WechatOrderPayController.isOneYuanSandboxOrder(order, "mall_demo_sandbox", false));
        order.setTotalAmount(new BigDecimal("3999.00"));
        assertFalse(WechatOrderPayController.isOneYuanSandboxOrder(order, "mall_demo_sandbox", true));
        order.setTotalAmount(new BigDecimal("1.00"));
        order.setBalanceAmount(new BigDecimal("1.00"));
        assertFalse(WechatOrderPayController.isOneYuanSandboxOrder(order, "mall_demo_sandbox", true));
    }
}
