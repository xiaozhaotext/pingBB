package com.macro.mall.portal.controller;

import com.macro.mall.model.OmsOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatProductionTestOrderGateTest {
    @Test void onlyExactCashOrderInProductionDatabasePasses() {
        OmsOrder order = new OmsOrder();
        order.setOrderSn("WU-REAL-ORDER");
        order.setBalanceAmount(BigDecimal.ZERO);
        assertTrue(WechatOrderPayController.isProductionTestOrder(order, "mall_demo", "WU-REAL-ORDER"));
        assertFalse(WechatOrderPayController.isProductionTestOrder(order, "mall_demo_sandbox", "WU-REAL-ORDER"));
        assertFalse(WechatOrderPayController.isProductionTestOrder(order, "mall_demo", "OTHER-ORDER"));
        order.setBalanceAmount(BigDecimal.ONE);
        assertFalse(WechatOrderPayController.isProductionTestOrder(order, "mall_demo", "WU-REAL-ORDER"));
    }

    @Test void oneYuanProductExceptionRequiresSingleMatchingCashItem() {
        OmsOrder order = new OmsOrder();
        order.setTotalAmount(BigDecimal.ONE);
        order.setPayAmount(BigDecimal.ONE);
        order.setBalanceAmount(BigDecimal.ZERO);
        assertTrue(WechatOrderPayController.isProductionOneYuanProductOrder(order, "mall_demo", 1, 1));
        assertFalse(WechatOrderPayController.isProductionOneYuanProductOrder(order, "mall_demo_sandbox", 1, 1));
        assertFalse(WechatOrderPayController.isProductionOneYuanProductOrder(order, "mall_demo", 0, 1));
        assertFalse(WechatOrderPayController.isProductionOneYuanProductOrder(order, "mall_demo", 1, 2));
        order.setPayAmount(new BigDecimal("0.50"));
        assertFalse(WechatOrderPayController.isProductionOneYuanProductOrder(order, "mall_demo", 1, 1));
    }
}
