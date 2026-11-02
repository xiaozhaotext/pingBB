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

    @Test void halfYuanProductExceptionRequiresSingleMatchingCashItem() {
        OmsOrder order = new OmsOrder();
        order.setTotalAmount(new BigDecimal("0.50"));
        order.setPayAmount(new BigDecimal("0.50"));
        order.setBalanceAmount(BigDecimal.ZERO);
        assertTrue(WechatOrderPayController.isProductionHalfYuanProductOrder(order, "mall_demo", 1, 1));
        assertFalse(WechatOrderPayController.isProductionHalfYuanProductOrder(order, "mall_demo_sandbox", 1, 1));
        assertFalse(WechatOrderPayController.isProductionHalfYuanProductOrder(order, "mall_demo", 0, 1));
        assertFalse(WechatOrderPayController.isProductionHalfYuanProductOrder(order, "mall_demo", 1, 2));
        order.setPayAmount(BigDecimal.ONE);
        assertFalse(WechatOrderPayController.isProductionHalfYuanProductOrder(order, "mall_demo", 1, 1));
    }
}
