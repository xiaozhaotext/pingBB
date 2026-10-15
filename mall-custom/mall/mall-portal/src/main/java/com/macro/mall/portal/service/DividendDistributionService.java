package com.macro.mall.portal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settles partner and cofounder pools by held shares and independent lifetime caps. */
@Service
public class DividendDistributionService {
    private final JdbcTemplate jdbc;
    private final MemberEarningsCreditService earningsCreditService;
    private final MembershipTierService tiers;

    @Value("${mall.dividend.payout.enabled:false}")
    private boolean payoutEnabled;

    public DividendDistributionService(JdbcTemplate jdbc, PointsWalletService ignored,
                                       MemberEarningsCreditService earningsCreditService) {
        this.jdbc = jdbc;
        this.earningsCreditService = earningsCreditService;
        this.tiers = new MembershipTierService(jdbc);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> quota(long memberId) {
        MembershipTierRules.Tier tier = tiers.tier(memberId);
        BigDecimal partnerReceived = received(memberId, "PARTNER");
        BigDecimal cofounderReceived = received(memberId, "COFOUNDER");
        BigDecimal operationsReceived = jdbc.queryForObject(
                "SELECT COALESCE(SUM(-change_amount),0) FROM bonus_dividend_pool_ledger"
                        + " WHERE pool_code='OPERATIONS' AND source_type='OPERATIONS_PAYOUT' AND receiver_member_id=?",
                BigDecimal.class, memberId);
        if (operationsReceived == null) operationsReceived = BigDecimal.ZERO;
        BigDecimal partnerRemaining = tier.partnerCap().subtract(partnerReceived).max(BigDecimal.ZERO);
        BigDecimal cofounderRemaining = tier.cofounderCap().subtract(cofounderReceived).max(BigDecimal.ZERO);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identityType", tier.code());
        result.put("identityName", tier.name());
        result.put("referralCount", tiers.paidDirectReferrals(memberId));
        result.put("partnerShares", tier.partnerShares());
        result.put("partnerCap", tier.partnerCap());
        result.put("partnerReceived", partnerReceived);
        result.put("partnerRemaining", partnerRemaining);
        result.put("cofounderShares", tier.cofounderShares());
        result.put("cofounderCap", tier.cofounderCap());
        result.put("cofounderReceived", cofounderReceived);
        result.put("cofounderRemaining", cofounderRemaining);
        result.put("capAmount", tier.partnerCap().add(tier.cofounderCap()));
        result.put("receivedAmount", partnerReceived.add(cofounderReceived));
        result.put("operationsReceivedAmount", operationsReceived);
        result.put("remainingAmount", partnerRemaining.add(cofounderRemaining));
        result.put("payoutEnabled", payoutEnabled && distributionApproved());
        result.put("updatedAt", LocalDateTime.now());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int distributeDaily(LocalDate statDate) {
        if (!payoutEnabled || !distributionApproved()) throw new IllegalStateException("每日分红发放未启用");
        BigDecimal aggregate = jdbc.queryForObject("SELECT pool_balance FROM dividend_pool WHERE id=1 FOR UPDATE",
                BigDecimal.class);
        if (aggregate == null) throw new IllegalStateException("总分红池不存在");
        return settlePool("PARTNER", statDate) + settlePool("COFOUNDER", statDate);
    }

    private record Candidate(long memberId, int shares, BigDecimal cap) { }

    private int settlePool(String poolCode, LocalDate statDate) {
        BigDecimal balance = jdbc.queryForObject("SELECT pool_balance FROM bonus_dividend_pool"
                + " WHERE pool_code=? FOR UPDATE", BigDecimal.class, poolCode);
        if (balance == null) throw new IllegalStateException("分红池不存在: " + poolCode);
        if (balance.signum() <= 0) return 0;
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM member_bonus_dividend_ledger"
                + " WHERE pool_code=? AND stat_date=?", Integer.class, poolCode, statDate);
        if (existing != null && existing > 0) return 0;
        Integer legacyExisting = jdbc.queryForObject("SELECT COUNT(*) FROM member_dividend_ledger"
                + " WHERE stat_date=? AND " + ("COFOUNDER".equals(poolCode) ? "shares=3" : "shares<>3"),
                Integer.class, statDate);
        if (legacyExisting != null && legacyExisting > 0) return 0;

        List<Long> memberIds = jdbc.queryForList("SELECT DISTINCT member_id FROM membership_purchase_order"
                + " WHERE level='PARTNER' AND status='PAID' AND amount=3999.00 ORDER BY member_id", Long.class);
        List<Candidate> candidates = new ArrayList<>();
        int totalShares = 0;
        for (long memberId : memberIds) {
            MembershipTierRules.Tier tier = tiers.tier(memberId);
            int shares = "PARTNER".equals(poolCode) ? tier.partnerShares() : tier.cofounderShares();
            if (shares <= 0) continue;
            candidates.add(new Candidate(memberId, shares,
                    "PARTNER".equals(poolCode) ? tier.partnerCap() : tier.cofounderCap()));
            totalShares += shares;
        }
        if (totalShares == 0) return 0;
        BigDecimal perShare = balance.divide(BigDecimal.valueOf(totalShares), 2, RoundingMode.DOWN);
        if (perShare.signum() <= 0) return 0;

        int paid = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Candidate candidate : candidates) {
            BigDecimal already = received(candidate.memberId(), poolCode);
            BigDecimal remaining = candidate.cap().subtract(already);
            if (remaining.signum() <= 0) continue;
            BigDecimal amount = MembershipTierRules.poolShare(balance, candidate.shares(), totalShares)
                    .min(remaining);
            if (amount.signum() <= 0) continue;
            jdbc.update("INSERT INTO member_bonus_dividend_ledger"
                            + "(member_id,pool_code,stat_date,shares,total_shares,dividend_amount,cap_amount,cumulative_received)"
                            + " VALUES(?,?,?,?,?,?,?,?)",
                    candidate.memberId(), poolCode, statDate, candidate.shares(), totalShares,
                    amount, candidate.cap(), already.add(amount));
            String key = "bonus-dividend:" + poolCode + ":" + candidate.memberId() + ":" + statDate;
            if (!earningsCreditService.credit(candidate.memberId(), amount, "dividend", statDate.toString(),
                    key, poolCode + "池分红 " + statDate)) {
                throw new IllegalStateException("分红收益入账失败，已回滚");
            }
            Long payoutId = jdbc.queryForObject("SELECT id FROM member_bonus_dividend_ledger"
                    + " WHERE member_id=? AND pool_code=? AND stat_date=?", Long.class,
                    candidate.memberId(), poolCode, statDate);
            totalPaid = totalPaid.add(amount);
            jdbc.update("INSERT INTO bonus_dividend_pool_ledger"
                            + "(pool_code,change_amount,balance_after,source_type,source_id,"
                            + "receiver_member_id,receiver_identity,remark)"
                            + " VALUES(?,?,?,'FOUR_TIER_DIVIDEND',?,?,?,?)",
                    poolCode, amount.negate(), balance.subtract(totalPaid), payoutId,
                    candidate.memberId(), poolCode, "每日分红 " + statDate);
            paid++;
        }
        if (totalPaid.signum() > 0) {
            if (jdbc.update("UPDATE bonus_dividend_pool SET pool_balance=pool_balance-?,total_out=total_out+?"
                            + " WHERE pool_code=? AND pool_balance>=?",
                    totalPaid, totalPaid, poolCode, totalPaid) != 1) {
                throw new IllegalStateException("奖金池余额不足，已回滚");
            }
            if (jdbc.update("UPDATE dividend_pool SET pool_balance=pool_balance-?,total_out=total_out+?"
                            + " WHERE id=1 AND pool_balance>=?", totalPaid, totalPaid, totalPaid) != 1) {
                throw new IllegalStateException("总分红池余额不足，已回滚");
            }
            jdbc.update("INSERT INTO dividend_pool_ledger(change_amount,balance_after,source_type,source_id,remark)"
                            + " SELECT ?*-1,pool_balance,'DAILY_DIVIDEND',NULL,? FROM dividend_pool WHERE id=1",
                    totalPaid, poolCode + "池 " + statDate + " 共 " + paid + " 人");
        }
        return paid;
    }

    private BigDecimal received(long memberId, String poolCode) {
        BigDecimal current = jdbc.queryForObject("SELECT COALESCE(SUM(dividend_amount),0)"
                + " FROM member_bonus_dividend_ledger WHERE member_id=? AND pool_code=?",
                BigDecimal.class, memberId, poolCode);
        BigDecimal legacy = jdbc.queryForObject("SELECT COALESCE(SUM(dividend_amount),0)"
                + " FROM member_dividend_ledger WHERE member_id=? AND "
                + ("COFOUNDER".equals(poolCode) ? "shares=3" : "shares<>3"),
                BigDecimal.class, memberId);
        return (current == null ? BigDecimal.ZERO : current).add(legacy == null ? BigDecimal.ZERO : legacy);
    }

    private boolean distributionApproved() {
        List<String> rows = jdbc.query("SELECT config_value FROM sys_config"
                + " WHERE config_key='dividend.distribution.enabled' LIMIT 1", (rs, n) -> rs.getString(1));
        return !rows.isEmpty() && ("1".equals(rows.get(0)) || "true".equalsIgnoreCase(rows.get(0)));
    }

}
