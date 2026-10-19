package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.ZoneRules;
import com.macro.mall.portal.domain.ZoneSettlement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三区购买规则隔离：自选区现金/余额、复购区复购积分+现金、消费区商品积分。
 * 专区之间不允许串用结算方式，混合下单时按专区分别计算再汇总现金应付金额。
 */
class ZoneSettlementServiceTest {
    private final JdbcTemplate jdbc = new JdbcTemplate() {
        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null; // No sys_config row: use documented settlement defaults.
        }
    };
    private final ZoneSettlementService service = new ZoneSettlementService(jdbc);

    /** 配置缺失时回退默认值：复购积分比例 30%、1 商品积分 = 1 元、最高抵扣 100%。 */
    private ZoneSettlementService.Wallets wallets(long productPoints, long repurchasePoints, String balance) {
        return new ZoneSettlementService.Wallets(productPoints, repurchasePoints, new BigDecimal(balance));
    }

    /** 金额按数值比较，避免 BigDecimal 小数位差异导致误判。 */
    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private ZoneSettlement.Plan plan(ZoneSettlement settlement, String zoneCode) {
        return settlement.getPlans().stream()
                .filter(item -> zoneCode.equals(item.getZoneCode()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("自选区现金支付：只按商品金额收现金，不使用任何积分")
    void pickZonePaysCashOnly() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("80.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, false, wallets(9999, 9999, "500.00"));
        ZoneSettlement.Plan pick = plan(settlement, ZoneRules.PICK);
        assertAmount("80.00", pick.getCashAmount());
        assertEquals(0, pick.getProductPoints());
        assertEquals(0, pick.getRepurchasePoints());
        assertAmount("0.00", pick.getBalanceAmount());
        assertTrue(pick.isPayable());
        assertAmount("80.00", settlement.getCashAmount());
    }

    @Test
    @DisplayName("自选区余额支付：余额足够时现金为 0，不能使用商品积分或复购积分")
    void pickZoneUsesBalanceWhenEnabled() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("60.00"), 2,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(9999, 9999, "200.00"));
        ZoneSettlement.Plan pick = plan(settlement, ZoneRules.PICK);
        assertAmount("120.00", pick.getBalanceAmount());
        assertAmount("0.00", pick.getCashAmount());
        assertEquals(0, pick.getProductPoints());
        assertEquals(0, pick.getRepurchasePoints());
        assertAmount("0.00", settlement.getCashAmount());
    }

    @Test
    @DisplayName("自选区余额不足：本专区不可下单，提示可改用现金")
    void pickZoneBlocksWhenBalanceNotEnough() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("100.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(9999, 9999, "30.00"));
        ZoneSettlement.Plan pick = plan(settlement, ZoneRules.PICK);
        assertFalse(pick.isPayable());
        assertAmount("100.00", pick.getCashAmount());
        assertTrue(pick.getBlockedReason().contains("余额不足"));
    }

    @Test
    @DisplayName("复购区 30% 复购积分 + 70% 现金")
    void rebuyZoneSplitsThirtySeventy() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, false, wallets(0, 500, "0.00"));
        ZoneSettlement.Plan rebuy = plan(settlement, ZoneRules.REBUY);
        assertEquals(30, rebuy.getRepurchasePoints());
        assertAmount("30.00", rebuy.getRepurchasePointAmount());
        assertAmount("70.00", rebuy.getCashAmount());
        assertEquals(0, rebuy.getProductPoints());
        assertTrue(rebuy.isPayable());
    }

    @Test
    @DisplayName("复购区：30%积分，现金部分先扣余额，不足差额留给外部支付")
    void rebuyZoneUsesPartialBalanceBeforeExternalPayment() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(0, 30, "40.00"));
        ZoneSettlement.Plan rebuy = plan(settlement, ZoneRules.REBUY);
        assertEquals(30, rebuy.getRepurchasePoints());
        assertAmount("30.00", rebuy.getRepurchasePointAmount());
        assertAmount("40.00", rebuy.getBalanceAmount());
        assertAmount("30.00", rebuy.getCashAmount());
        assertAmount("40.00", settlement.getBalanceAmount());
        assertAmount("30.00", settlement.getCashAmount());
        assertTrue(rebuy.isPayable());
    }

    @Test
    @DisplayName("复购区：积分及余额足额时现金为零")
    void rebuyZoneCanFinishWithoutExternalPayment() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(0, 30, "100.00"));
        assertAmount("70.00", plan(settlement, ZoneRules.REBUY).getBalanceAmount());
        assertAmount("0.00", settlement.getCashAmount());
    }

    @Test
    @DisplayName("混合订单先为自选区预留余额，复购区不能重复抵扣")
    void mixedCartDoesNotSpendBalanceTwice() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO),
                        new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("80.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(0, 30, "100.00"));
        assertAmount("80.00", plan(settlement, ZoneRules.PICK).getBalanceAmount());
        assertAmount("20.00", plan(settlement, ZoneRules.REBUY).getBalanceAmount());
        assertAmount("50.00", settlement.getCashAmount());
        assertAmount("100.00", settlement.getBalanceAmount());
    }

    @Test
    @DisplayName("复购区选择全现金时不扣复购积分")
    void rebuyZoneCanPayAllCash() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                false, false, wallets(0, 500, "0.00"));
        ZoneSettlement.Plan rebuy = plan(settlement, ZoneRules.REBUY);
        assertEquals(0, rebuy.getRepurchasePoints());
        assertAmount("100.00", rebuy.getCashAmount());
        assertTrue(rebuy.isPayable());
    }

    @Test
    @DisplayName("复购区复购积分不足：不可下单，也不能改用商品积分")
    void rebuyZoneBlocksWithoutEnoughRepurchasePoints() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, false, wallets(9999, 10, "0.00"));
        ZoneSettlement.Plan rebuy = plan(settlement, ZoneRules.REBUY);
        assertFalse(rebuy.isPayable());
        assertEquals(0, rebuy.getProductPoints());
        assertAmount("100.00", rebuy.getCashAmount());
    }

    @Test
    @DisplayName("消费区只用商品积分：现金为 0，积分向上取整保证全额抵扣")
    void consumeZonePaysWithProductPointsOnly() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("19.90"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(100, 9999, "500.00"));
        ZoneSettlement.Plan consume = plan(settlement, ZoneRules.CONSUME);
        assertEquals(20, consume.getProductPoints());
        assertAmount("19.90", consume.getProductPointAmount());
        assertAmount("0.00", consume.getCashAmount());
        assertAmount("0.00", consume.getBalanceAmount());
        assertEquals(0, consume.getRepurchasePoints());
        assertTrue(consume.isPayable());
        assertAmount("0.00", settlement.getCashAmount());
    }

    @Test
    @DisplayName("消费区商品积分不足：不允许下单")
    void consumeZoneBlocksWithoutEnoughProductPoints() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("50.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                false, false, wallets(10, 9999, "500.00"));
        ZoneSettlement.Plan consume = plan(settlement, ZoneRules.CONSUME);
        assertFalse(consume.isPayable());
        assertAmount("50.00", consume.getCashAmount());
        assertTrue(consume.getBlockedReason().contains("产品积分不足"));
    }

    @Test
    @DisplayName("混合下单：按专区分别计算，最终现金 = 各专区现金部分之和")
    void mixedCartSumsCashPerZone() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("50.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO),
                        new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO),
                        new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("30.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO)),
                true, false, wallets(50, 30, "0.00"));
        assertEquals(3, settlement.getPlans().size());
        assertAmount("50.00", plan(settlement, ZoneRules.PICK).getCashAmount());
        assertAmount("70.00", plan(settlement, ZoneRules.REBUY).getCashAmount());
        assertAmount("0.00", plan(settlement, ZoneRules.CONSUME).getCashAmount());
        assertAmount("120.00", settlement.getCashAmount());
        assertAmount("180.00", settlement.getTotalAmount());
    }

    @Test
    @DisplayName("未分配专区的商品按自选区规则结算，不会越界使用消费区或复购区积分")
    void unassignedZoneFollowsPickRules() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.NONE, new BigDecimal("40.00"), 1,
                        BigDecimal.ZERO, BigDecimal.ZERO)),
                true, false, wallets(9999, 9999, "100.00"));
        ZoneSettlement.Plan pick = plan(settlement, ZoneRules.PICK);
        assertAmount("40.00", pick.getCashAmount());
        assertEquals(0, pick.getProductPoints());
        assertEquals(0, pick.getRepurchasePoints());
        assertAmount("40.00", settlement.getCashAmount());
    }

    @Test
    @DisplayName("优惠券与活动优惠在专区内抵扣，不影响其他专区的应收金额")
    void promotionAndCouponStayInsideOwnZone() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("100.00"), 1,
                                new BigDecimal("10.00"), new BigDecimal("5.00")),
                        new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("20.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO)),
                false, false, wallets(20, 0, "0.00"));
        ZoneSettlement.Plan pick = plan(settlement, ZoneRules.PICK);
        assertAmount("85.00", pick.getNetAmount());
        assertAmount("85.00", pick.getCashAmount());
        assertAmount("20.00", plan(settlement, ZoneRules.CONSUME).getProductPointAmount());
        assertAmount("85.00", settlement.getCashAmount());
    }

    @Test
    @DisplayName("存在不可结算专区时，汇总现金应付不包含该专区金额，避免展示无法支付的金额")
    void blockedZoneIsExcludedFromSummaryCash() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("100.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO),
                        new ZoneSettlementService.Line(ZoneRules.CONSUME, new BigDecimal("50.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO)),
                false, false, wallets(0, 0, "0.00"));
        assertAmount("100.00", plan(settlement, ZoneRules.PICK).getCashAmount());
        assertFalse(plan(settlement, ZoneRules.CONSUME).isPayable());
        assertAmount("50.00", plan(settlement, ZoneRules.CONSUME).getCashAmount());
        assertAmount("100.00", settlement.getCashAmount());
        assertAmount("150.00", settlement.getTotalAmount());
    }

    @Test
    @DisplayName("自选区余额不足时该专区不可结算，汇总现金应付只统计可结算专区")
    void blockedBalanceZoneIsExcludedFromSummaryCash() {
        ZoneSettlement settlement = service.settle(
                List.of(new ZoneSettlementService.Line(ZoneRules.PICK, new BigDecimal("100.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO),
                        new ZoneSettlementService.Line(ZoneRules.REBUY, new BigDecimal("100.00"), 1,
                                BigDecimal.ZERO, BigDecimal.ZERO)),
                true, true, wallets(0, 9999, "10.00"));
        assertFalse(plan(settlement, ZoneRules.PICK).isPayable());
        assertTrue(plan(settlement, ZoneRules.REBUY).isPayable());
        assertAmount("60.00", settlement.getCashAmount());
        assertAmount("10.00", settlement.getBalanceAmount());
        assertAmount("30.00", settlement.getRepurchasePointAmount());
    }
}
