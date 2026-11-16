package com.macro.mall.portal.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 验证线上已有的全额智豆或全额余额结算，测试不连接业务数据库。 */
class ConsumeRuntimeAlignmentTest {
    private final ZoneSettlementService service = new ZoneSettlementService(null);
    private ZoneSettlementService.Line line(String price, int quantity, int rate) {
        return new ZoneSettlementService.Line("CONSUME", new BigDecimal(price), quantity,
                new BigDecimal("1"), BigDecimal.ZERO, rate);
    }
    @Test void usesEachProductsRateAndRoundsUpPerLine() {
        var result = service.settle(List.of(line("1.01", 2, 3), line("2", 1, 100)), false, false, true,
                new ZoneSettlementService.Wallets(207, 0, BigDecimal.ZERO));
        var plan = result.getPlans().get(0);
        assertTrue(plan.isPayable());
        assertEquals(207, plan.getProductPoints());
        assertEquals(0, new BigDecimal("4.02").compareTo(plan.getProductPointAmount()));
        assertEquals(2, result.getConsumeLineCash().size());
        assertEquals(0, plan.getCashAmount().signum());
    }
    @Test void insufficientPointsDoNotFallBackToBalanceOrWechat() {
        var plan = service.settle(List.of(line("10", 1, 100)), false, true, true,
                new ZoneSettlementService.Wallets(999, 0, new BigDecimal("100"))).getPlans().get(0);
        assertFalse(plan.isPayable());
        assertEquals("智豆不足，请切换余额支付", plan.getBlockedReason());
        assertEquals(0, plan.getBalanceAmount().signum());
        assertEquals(0, plan.getCashAmount().signum());
    }
    @Test void balanceChoiceRequiresFullGrossPriceAndDoesNotUsePoints() {
        for (String balance : List.of("9.99", "10")) {
            var plan = service.settle(List.of(line("10", 1, 100)), false, false, false,
                    new ZoneSettlementService.Wallets(9999, 0, new BigDecimal(balance))).getPlans().get(0);
            assertEquals(balance.equals("10"), plan.isPayable());
            assertEquals(0, plan.getProductPoints());
            assertEquals(0, plan.getCashAmount().signum());
            if (plan.isPayable()) assertEquals(0, new BigDecimal("10").compareTo(plan.getBalanceAmount()));
        }
    }
}
