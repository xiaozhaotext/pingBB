package com.macro.mall.common.membership;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Versioned rules confirmed on 2026-10-16. No database or payment side effects. */
public final class TriPoolRules {
    private TriPoolRules() { }
    public static final String VERSION = "2026-10-16-v2";
    public static final BigDecimal PRICE = new BigDecimal("3999.00");
    public static final BigDecimal REFERRAL_CASH = new BigDecimal("399.90");
    public static final long BUYER_POINTS = 3999;
    public static final long REFERRER_POINTS = 1200;
    public enum Pool { PARTNER, COFOUNDER, OPERATIONS }
    public enum Tier { VISITOR, MEMBER, PARTNER, COFOUNDER }
    public record Position(long memberId, Tier tier, long shares, BigDecimal cap, BigDecimal received) { }
    public record Award(long memberId, Tier tier, long shares, long totalShares,
                        BigDecimal cap, BigDecimal receivedBefore, BigDecimal amount) { }

    public static Tier tier(boolean paidMember, long referrals) {
        validateReferrals(referrals);
        return !paidMember ? Tier.VISITOR : referrals >= 9 ? Tier.COFOUNDER
                : referrals >= 3 ? Tier.PARTNER : Tier.MEMBER;
    }

    public static long partnerShares(long referrals) {
        validateReferrals(referrals);
        return Math.addExact(1, referrals);
    }

    public static BigDecimal partnerCap(long referrals) {
        validateReferrals(referrals);
        if (referrals < 3) return BigDecimal.valueOf((referrals + 1) * 1200).setScale(2);
        // Each completed group of three adds 4000 * 2 = 8000; remaining
        // direct paid referrals add 1200 each. BigDecimal avoids long overflow.
        return BigDecimal.valueOf(referrals / 3).multiply(BigDecimal.valueOf(8000))
                .add(BigDecimal.valueOf(referrals % 3).multiply(BigDecimal.valueOf(1200))).setScale(2);
    }

    /** Partner and cofounder distributions draw from one cumulative member cap. */
    public static BigDecimal poolCap(Pool pool, long referrals, BigDecimal otherPoolReceived) {
        if (pool == Pool.OPERATIONS || otherPoolReceived == null || otherPoolReceived.signum() < 0)
            throw new IllegalArgumentException("Invalid shared dividend cap input");
        BigDecimal sharedRemaining = partnerCap(referrals).subtract(otherPoolReceived).max(BigDecimal.ZERO);
        return pool == Pool.COFOUNDER ? sharedRemaining.min(new BigDecimal("1200.00")) : sharedRemaining;
    }

    public static BigDecimal contribution(Pool pool) {
        return new BigDecimal(pool == Pool.PARTNER ? "500.00" : "200.00");
    }

    /** Capped members are excluded; truncated cents and cap leftovers remain in the pool. */
    public static List<Award> allocate(BigDecimal balance, List<Position> positions) {
        if (balance == null || balance.signum() < 0 || balance.scale() > 2)
            throw new IllegalArgumentException("Invalid pool balance");
        Set<Long> seen = new HashSet<>();
        List<Position> eligible = new ArrayList<>();
        long total = 0;
        for (Position p : positions) {
            if (p.memberId <= 0 || !seen.add(p.memberId) || p.shares <= 0 || p.received == null
                    || p.received.signum() < 0 || (p.cap != null && p.cap.signum() < 0))
                throw new IllegalArgumentException("Invalid or duplicate dividend position");
            if (p.cap != null && p.received.compareTo(p.cap) >= 0) continue;
            eligible.add(p);
            total = Math.addExact(total, p.shares);
        }
        if (total == 0 || balance.signum() == 0) return List.of();
        List<Award> awards = new ArrayList<>();
        for (Position p : eligible) {
            BigDecimal amount = balance.multiply(BigDecimal.valueOf(p.shares))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN);
            if (p.cap != null) amount = amount.min(p.cap.subtract(p.received)).setScale(2, RoundingMode.DOWN);
            if (amount.signum() > 0) awards.add(new Award(p.memberId, p.tier, p.shares, total,
                    p.cap, p.received, amount));
        }
        return List.copyOf(awards);
    }

    private static void validateReferrals(long referrals) {
        if (referrals < 0) throw new IllegalArgumentException("Negative referral count");
    }
}
