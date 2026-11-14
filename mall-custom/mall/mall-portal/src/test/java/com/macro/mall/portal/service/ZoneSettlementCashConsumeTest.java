package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.ZoneRules;
import com.macro.mall.portal.domain.ZoneSettlement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneSettlementCashConsumeTest {
    private final ZoneSettlementService service = new ZoneSettlementService(null) {
        @Override public BigDecimal rebuyPointsRatio() { return new BigDecimal("0.30"); }
        @Override public BigDecimal productPointYuanPerPoint() { return BigDecimal.ONE; }
        @Override public BigDecimal productPointMaxRatio() { return BigDecimal.ONE; }
    };

    @Test
    void consumeUsesCashEvenWhenMemberHasPointsAndBalance() {
        ZoneSettlement result = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("99.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)), true, true, true,
                new ZoneSettlementService.Wallets(5000, 5000, new BigDecimal("5000.00")));
        ZoneSettlement.Plan plan = result.getPlans().get(0);
        assertTrue(plan.isPayable());
        assertEquals(0, plan.getProductPoints());
        assertEquals(0, plan.getRepurchasePoints());
        assertEquals(0, plan.getProductPointAmount().signum());
        assertEquals(0, plan.getBalanceAmount().signum());
        assertEquals(new BigDecimal("99.00"), plan.getCashAmount());
        assertEquals(new BigDecimal("99.00"), result.getCashAmount());
    }

    @Test
    void consumeStillUsesCashWhenLegacyClientDisablesPoints() {
        ZoneSettlement result = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("10.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)), false, false, false,
                new ZoneSettlementService.Wallets(0, 0, BigDecimal.ZERO));
        assertTrue(result.getPlans().get(0).isPayable());
        assertEquals(new BigDecimal("10.00"), result.getCashAmount());
    }
}
