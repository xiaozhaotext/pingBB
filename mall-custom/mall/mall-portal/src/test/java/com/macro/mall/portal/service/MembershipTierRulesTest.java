package com.macro.mall.portal.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MembershipTierRulesTest {
    @Test void tenPeopleWithTwelveSharesConsumeExactlyOnePool() {
        BigDecimal pool = new BigDecimal("1200.00");
        BigDecimal twoShares = MembershipTierRules.poolShare(pool, 2, 12);
        BigDecimal oneShare = MembershipTierRules.poolShare(pool, 1, 12);
        assertEquals(new BigDecimal("200.00"), twoShares);
        assertEquals(new BigDecimal("100.00"), oneShare);
        assertEquals(pool, twoShares.multiply(BigDecimal.valueOf(2))
                .add(oneShare.multiply(BigDecimal.valueOf(8))));
    }

    @Test void tiersFollowOnlyPaidDirectMemberThresholds() {
        assertTier(false, 9, 0, 0, 0, 0, 0);
        assertTier(true, 0, 1, 1, 1200, 0, 0);
        assertTier(true, 2, 1, 3, 1200, 0, 0);
        assertTier(true, 3, 2, 4, 8000, 0, 0);
        assertTier(true, 5, 2, 6, 8000, 0, 0);
        assertTier(true, 6, 2, 7, 16000, 0, 0);
        assertTier(true, 9, 3, 10, 24000, 1, 1200);
        assertTier(true, 12, 3, 13, 24000, 1, 1200);
    }

    private static void assertTier(boolean paid, int refs, int identity, int partnerShares,
                                   long partnerCap, int cofounderShares, long cofounderCap) {
        var tier = MembershipTierRules.forMember(paid, refs);
        assertEquals(identity, tier.code());
        assertEquals(partnerShares, tier.partnerShares());
        assertEquals(BigDecimal.valueOf(partnerCap).setScale(2), tier.partnerCap());
        assertEquals(cofounderShares, tier.cofounderShares());
        assertEquals(BigDecimal.valueOf(cofounderCap).setScale(2), tier.cofounderCap());
    }
}
