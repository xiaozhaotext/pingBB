package com.macro.mall.portal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Real daily dividend distribution.
 * Rules (from product mind map):
 * - PARTNER (identity_type=1): 1 share; COFOUNDER (identity_type=2): 3 shares
 * - Daily pool is distributed proportionally by shares
 * - Per-member cap based on referral count: base 1200, +1200 per referral, max 12000
 * - Idempotent per (member_id, stat_date)
 */
@Service
public class DividendDistributionService {

    private final JdbcTemplate jdbc;
    private final PointsWalletService pointsWallet;
    private final MemberEarningsCreditService earningsCreditService;

    // 规则未定稿前，部署配置和后台开关必须同时开启才能真实入账。
    @Value("${mall.dividend.payout.enabled:false}")
    private boolean payoutEnabled;

    public DividendDistributionService(JdbcTemplate jdbc, PointsWalletService pointsWallet,
                                       MemberEarningsCreditService earningsCreditService) {
        this.jdbc = jdbc;
        this.pointsWallet = pointsWallet;
        this.earningsCreditService = earningsCreditService;
    }

    /**
     * Distribute the dividend pool for the given date.
     * Returns the number of members paid.
     */
    @Transactional(rollbackFor = Exception.class)
    public int distributeDaily(LocalDate statDate) {
        // No config or consumer request can enable cash posting before business/compliance review.
        if (!payoutEnabled || !distributionApproved()) throw new IllegalStateException("每日分红发放未启用");
        BigDecimal poolBalance = jdbc.queryForObject(
                "SELECT pool_balance FROM dividend_pool WHERE id=1 FOR UPDATE", BigDecimal.class);
        if (poolBalance == null || poolBalance.signum() <= 0) return 0;

        // All dividend-eligible members: PARTNER=1 share, COFOUNDER=3 shares
        List<Map<String, Object>> members = jdbc.queryForList(
                "SELECT member_id, identity_type FROM ums_member_identity"
                        + " WHERE status=1 AND identity_type IN (1,2)");
        if (members.isEmpty()) return 0;

        int totalShares = 0;
        for (Map<String, Object> m : members) {
            int type = ((Number) m.get("identity_type")).intValue();
            totalShares += (type == 2 ? 3 : 1);
        }
        if (totalShares <= 0) return 0;

        // 每份分红：默认按池余额等比分配；若后台配置了「每日每份分红基数」，
        // 则以该基数为上限（池余额不足时自动缩水到池能支撑的金额）。
        BigDecimal perShare = poolBalance.divide(new BigDecimal(totalShares), 2, RoundingMode.DOWN);
        BigDecimal dailyBase = configDecimal("dividend.daily.base", BigDecimal.ZERO);
        if (dailyBase.signum() > 0 && dailyBase.compareTo(perShare) < 0) {
            perShare = dailyBase.setScale(2, RoundingMode.DOWN);
        }
        if (perShare.signum() <= 0) return 0;

        BigDecimal capBase = configDecimal("dividend.cap.base", new BigDecimal("1200.00"));
        BigDecimal capPerReferral = configDecimal("dividend.cap.per_referral", new BigDecimal("1200.00"));
        BigDecimal capMax = configDecimal("dividend.cap.max", new BigDecimal("12000.00"));
        int maxReferrals = configInt("dividend.cap.max_referrals", 999999);

        int paid = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Map<String, Object> m : members) {
            long memberId = ((Number) m.get("member_id")).longValue();
            int type = ((Number) m.get("identity_type")).intValue();
            int shares = (type == 2 ? 3 : 1);

            // Idempotency: skip if already paid for this date
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM member_dividend_ledger WHERE member_id=? AND stat_date=?",
                    Integer.class, memberId, statDate);
            if (exists != null && exists > 0) continue;

            // Referral count for cap
            Integer refCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ums_member_recommend WHERE inviter_id=?",
                    Integer.class, memberId);
            int refs = refCount == null ? 0 : refCount;
            BigDecimal cap = capBase.add(capPerReferral.multiply(new BigDecimal(Math.min(refs, maxReferrals))));
            if (cap.compareTo(capMax) > 0) cap = capMax;

            // Already received (cumulative)
            BigDecimal received = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(dividend_amount),0) FROM member_dividend_ledger WHERE member_id=?",
                    BigDecimal.class, memberId);
            if (received == null) received = BigDecimal.ZERO;

            BigDecimal remaining = cap.subtract(received);
            if (remaining.signum() <= 0) continue;

            BigDecimal amount = perShare.multiply(new BigDecimal(shares)).setScale(2, RoundingMode.DOWN);
            if (amount.compareTo(remaining) > 0) amount = remaining;
            if (amount.signum() <= 0) continue;

            BigDecimal cumulative = received.add(amount);
            jdbc.update("INSERT INTO member_dividend_ledger"
                            + "(member_id,stat_date,shares,dividend_amount,cap_amount,cumulative_received,calc_batch)"
                            + " VALUES(?,?,?,?,?,?,?)",
                    memberId, statDate, shares, amount, cap, cumulative, statDate.toString());

            // 入账到统一收益账本 ums_earnings：消费者端「我的收益/提现」与后台「佣金」读的都是这一套账本
            boolean credited = earningsCreditService.credit(memberId, amount, "dividend", statDate.toString(),
                    "dividend:" + memberId + ":" + statDate, "每日分红 " + statDate);

            if (!credited) throw new IllegalStateException("分红账本与收益账本不一致，已回滚");
            totalPaid = totalPaid.add(amount);
            paid++;
        }

        // Deduct from pool
        if (totalPaid.signum() > 0) {
            int changed = jdbc.update("UPDATE dividend_pool SET pool_balance=pool_balance-?,total_out=total_out+? WHERE id=1 AND pool_balance>=?",
                    totalPaid, totalPaid, totalPaid);
            if (changed != 1) throw new IllegalStateException("分红池余额不足，已回滚");
            jdbc.update("INSERT INTO dividend_pool_ledger(change_amount,balance_after,source_type,source_id,remark)"
                            + " SELECT ?*-1, pool_balance, 'DAILY_DIVIDEND', NULL, ? FROM dividend_pool WHERE id=1",
                    totalPaid, "每日分红 " + statDate + "，共 " + paid + " 人");
        }
        return paid;
    }

    /** 后台分红发放开关，缺失时保持关闭。 */
    private boolean distributionApproved() {
        return configBool("dividend.distribution.enabled", false);
    }

    private boolean configBool(String key, boolean fallback) {
        try {
            List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                    (rs, n) -> rs.getString(1), key);
            if (!rows.isEmpty() && rows.get(0) != null) {
                String v = rows.get(0).trim();
                if ("1".equals(v) || "true".equalsIgnoreCase(v)) return true;
                if ("0".equals(v) || "false".equalsIgnoreCase(v)) return false;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private BigDecimal configDecimal(String key, BigDecimal fallback) {
        try {
            List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                    (rs, n) -> rs.getString(1), key);
            if (!rows.isEmpty() && rows.get(0) != null) {
                BigDecimal v = new BigDecimal(rows.get(0).trim());
                if (v.signum() >= 0) return v;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private int configInt(String key, int fallback) {
        try {
            List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                    (rs, n) -> rs.getString(1), key);
            if (!rows.isEmpty() && rows.get(0) != null) {
                int v = Integer.parseInt(rows.get(0).trim());
                if (v >= 0) return v;
            }
        } catch (Exception ignored) { }
        return fallback;
    }
}
