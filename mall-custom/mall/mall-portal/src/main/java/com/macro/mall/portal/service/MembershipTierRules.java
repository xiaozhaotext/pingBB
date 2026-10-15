package com.macro.mall.portal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The public four-tier plan; referral counts include only paid 3999-yuan members. */
public final class MembershipTierRules {
    private MembershipTierRules() { }

    public record Tier(int code, String name, int partnerShares, BigDecimal partnerCap,
                       int cofounderShares, BigDecimal cofounderCap) { }

    public static Tier forMember(boolean purchasedMembership, int paidDirectReferrals) {
        if (!purchasedMembership) return new Tier(0, "游客", 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        int partnerShares = Math.addExact(1, Math.max(0, paidDirectReferrals));
        if (paidDirectReferrals >= 9) return new Tier(3, "联创", partnerShares, amount(24000), 1, amount(1200));
        if (paidDirectReferrals >= 6) return new Tier(2, "合伙人", partnerShares, amount(16000), 0, BigDecimal.ZERO);
        if (paidDirectReferrals >= 3) return new Tier(2, "合伙人", partnerShares, amount(8000), 0, BigDecimal.ZERO);
        return new Tier(1, "会员", partnerShares, amount(1200), 0, BigDecimal.ZERO);
    }

    public static BigDecimal poolShare(BigDecimal poolAmount, int heldShares, int totalShares) {
        if (poolAmount == null || poolAmount.signum() < 0 || heldShares < 0 || totalShares <= 0
                || heldShares > totalShares) throw new IllegalArgumentException("无效的分红池份数或金额");
        return poolAmount.divide(BigDecimal.valueOf(totalShares), 2, RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(heldShares));
    }

    private static BigDecimal amount(long value) { return BigDecimal.valueOf(value).setScale(2); }
}
