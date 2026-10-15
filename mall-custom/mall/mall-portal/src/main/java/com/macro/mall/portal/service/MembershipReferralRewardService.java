package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.domain.UmsPointsAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Direct-referrer reward, posted within the verified payment transaction. */
@Service
public class MembershipReferralRewardService {
    private final JdbcTemplate jdbc;
    private final PointsWalletService pointsWallet;
    private final MemberEarningsCreditService earningsCreditService;

    public record Snapshot(long referrerMemberId, BigDecimal cashRate, BigDecimal cashCap,
                           long repurchasePoints) { }

    public MembershipReferralRewardService(JdbcTemplate jdbc, PointsWalletService pointsWallet,
                                           MemberEarningsCreditService earningsCreditService) {
        this.jdbc = jdbc;
        this.pointsWallet = pointsWallet;
        this.earningsCreditService = earningsCreditService;
    }

    /** Snapshot the real direct parent and configured reward before asking for payment. */
    public Snapshot snapshotFor(long buyingMemberId, String level) {
        List<Map<String, Object>> referrers = jdbc.queryForList(
                "SELECT inviter_id,bind_time FROM ums_member_recommend WHERE member_id=? LIMIT 1",
                buyingMemberId);
        if (referrers.isEmpty()) return null;
        long referrerId = ((Number) referrers.get(0).get("inviter_id")).longValue();
        if (referrerId == buyingMemberId) throw unavailable();
        // Registration alone is a tourist account and carries no invitation reward.
        Integer paidMembership = jdbc.queryForObject("SELECT COUNT(*) FROM membership_purchase_order"
                        + " WHERE member_id=? AND level='PARTNER' AND status='PAID' AND amount=3999.00"
                        + " AND paid_at<=?", Integer.class, referrerId, referrers.get(0).get("bind_time"));
        if (paidMembership == null || paidMembership == 0) return null;
        if (!"PAID_AMOUNT".equals(config("membership.referral.reward_basis"))) throw unavailable();
        BigDecimal rate = decimal(config("membership.referral.cash_rate"));
        String prefix = "PARTNER".equals(level) ? "membership.referral.partner."
                : "membership.referral.cofounder.";
        String capText = config(prefix + "cash_cap");
        BigDecimal cap = capText == null || capText.isBlank() ? null : decimal(capText);
        long points = nonNegativeLong(config(prefix + "repurchase_points"));
        if ("PARTNER".equals(level)) {
            if (rate.compareTo(new BigDecimal("0.10")) != 0 || cap != null || points != 1200) throw unavailable();
        } else if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0 || rate.scale() > 6
                || (cap != null && (cap.signum() <= 0 || cap.scale() > 2))) {
            throw unavailable();
        }
        return new Snapshot(referrerId, rate, cap, points);
    }

    /** The caller holds the order row lock and owns the surrounding database transaction. */
    public void post(long orderId, long buyingMemberId, String level, BigDecimal paidAmount,
                     Long referrerId, BigDecimal rate, BigDecimal cap, Long repurchasePoints) {
        if (referrerId == null) return;
        if (referrerId == buyingMemberId || rate == null || rate.signum() <= 0
                || repurchasePoints == null || repurchasePoints < 0) throw unavailable();
        BigDecimal cash = calculateCash(paidAmount, rate, cap);
        if (cash.signum() <= 0) throw unavailable();
        // The unique order_id constraint is an additional safeguard alongside the locked order.
        jdbc.update("INSERT INTO member_referral_reward(member_id,from_member_id,order_id,basis_amount,"
                        + "reward_rate,reward_cap,reward_amount,reward_points,status)"
                        + " VALUES(?,?,?,?,?,?,?,?,'POSTED')",
                referrerId, buyingMemberId, orderId, paidAmount, rate, cap, cash, repurchasePoints);

        BigDecimal before = earningsCreditService.availableBalance(referrerId);
        boolean posted = earningsCreditService.credit(referrerId, cash, "referral_reward",
                Long.toString(orderId), "membership:referral:" + orderId, "直属会员支付权益奖励");
        if (!posted) throw new IllegalStateException("直推现金奖励入账失败（幂等或金额异常）");
        BigDecimal after = earningsCreditService.availableBalance(referrerId);

        String pointsBefore = "unchanged";
        String pointsAfter = "unchanged";
        if (repurchasePoints > 0) {
            CommonResult<Void> result = pointsWallet.changePoints(referrerId, PointsWalletService.TYPE_REPURCHASE,
                    "earn", repurchasePoints, Long.toString(orderId), "membership:referral:" + orderId,
                    null, "直属会员支付权益奖励");
            if (result.getCode() != 200) throw new IllegalStateException("直推复购积分入账失败: " + result.getMessage());
            UmsPointsAccount account = pointsWallet.getAccount(referrerId, PointsWalletService.TYPE_REPURCHASE);
            if (account == null || account.getBalance() == null) throw new IllegalStateException("直推复购积分余额读取失败");
            pointsBefore = Long.toString(account.getBalance() - repurchasePoints);
            pointsAfter = Long.toString(account.getBalance());
            jdbc.update("INSERT INTO member_point_ledger"
                            + "(member_id,point_type,change_amount,balance_after,source_type,source_id)"
                            + " VALUES(?,'REPURCHASE',?,?,'REFERRAL_REWARD',?)",
                    referrerId, repurchasePoints, account.getBalance(), orderId);
        }
        jdbc.update("INSERT INTO sys_audit_log(operator_name,module,action,target_id,detail,create_time,"
                        + "`operator`,target_type,old_value,new_value,created_at)"
                        + " VALUES('SYSTEM:payment','membership','REFERRAL_REWARD',?,?,CURRENT_TIMESTAMP,"
                        + "'SYSTEM:payment','member_referral_reward',?,?,CURRENT_TIMESTAMP)",
                orderId, "直属会员 " + buyingMemberId + " 购买 " + level + "；推荐人 " + referrerId,
                "cash=" + before + ";repurchasePoints=" + pointsBefore,
                "cash=" + after + ";repurchasePoints=" + pointsAfter);
    }

    public static BigDecimal calculateCash(BigDecimal basis, BigDecimal rate, BigDecimal cap) {
        BigDecimal reward = basis.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return cap != null && reward.compareTo(cap) > 0 ? cap.setScale(2, RoundingMode.HALF_UP) : reward;
    }

    private String config(String key) {
        List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                (rs, n) -> rs.getString(1), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal decimal(String text) {
        try { return new BigDecimal(text); }
        catch (Exception ex) { throw unavailable(); }
    }

    /** 未配置或配置为 NULL/空 时按 0 处理（即不发放积分），而不是让整笔奖励失败。 */
    private long nonNegativeLong(String text) {
        if (text == null || text.isBlank() || "NULL".equalsIgnoreCase(text.trim())) return 0;
        try {
            long value = Long.parseLong(text.trim());
            if (value >= 0) return value;
        } catch (Exception ignored) { }
        throw unavailable();
    }

    private MembershipPaymentException unavailable() {
        return new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
    }
}
