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
}
