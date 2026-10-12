package com.macro.mall.portal.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0-1e 直推现金奖励计算规则：
 * 联创 10000 × 10% 上限 200 → 200.00；合伙人 3999 × 10% 无上限 → 399.90。
 */
class MembershipReferralRewardTest {

    @Test
    void cofounderRewardCappedAt200() {
        assertEquals(new BigDecimal("200.00"),
                MembershipVerifiedPaymentHandler.calculateReferralCash(
                        new BigDecimal("10000.00"), new BigDecimal("0.10"), new BigDecimal("200.00")));
    }

    @Test
    void partnerRewardHasNoCap() {
        assertEquals(new BigDecimal("399.90"),
                MembershipVerifiedPaymentHandler.calculateReferralCash(
                        new BigDecimal("3999.00"), new BigDecimal("0.10"), null));
    }

    @Test
    void rewardBelowCapKeepsFullAmount() {
        assertEquals(new BigDecimal("100.00"),
                MembershipVerifiedPaymentHandler.calculateReferralCash(
                        new BigDecimal("1000.00"), new BigDecimal("0.10"), new BigDecimal("200.00")));
    }
}
