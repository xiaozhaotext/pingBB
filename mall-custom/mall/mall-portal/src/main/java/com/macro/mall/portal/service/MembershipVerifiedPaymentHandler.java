package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.domain.UmsMemberIdentity;
import com.macro.mall.common.api.CommonResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Durable, repeatable fulfillment of a provider-verified membership payment. */
@Service
public class MembershipVerifiedPaymentHandler {
    private final JdbcTemplate jdbc;
    private final PointsWalletService pointsWallet;
    private final MemberIdentityService identities;

    public MembershipVerifiedPaymentHandler(JdbcTemplate jdbc, PointsWalletService pointsWallet,
                                            MemberIdentityService identities) {
        this.jdbc = jdbc;
        this.pointsWallet = pointsWallet;
        this.identities = identities;
    }

    @Transactional(rollbackFor = Exception.class)
    public void accept(MembershipPaymentGateway.VerifiedPayment payment) {
        if (payment == null || payment.amount() == null || payment.amount().signum() <= 0
                || payment.merchantOrderNo() == null || payment.transactionId() == null) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,member_id,level,amount,product_points,repurchase_points,pay_channel,status,transaction_id"
                        + " FROM membership_purchase_order WHERE merchant_order_no=? FOR UPDATE", payment.merchantOrderNo());
        if (rows.size() != 1) throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        Map<String, Object> order = rows.get(0);
        long orderId = ((Number) order.get("id")).longValue();
        if ("PAID".equals(order.get("status"))) {
            if (payment.transactionId().equals(order.get("transaction_id"))
                    && payment.channel().equals(order.get("pay_channel"))
                    && payment.amount().compareTo((BigDecimal) order.get("amount")) == 0) return;
            throw new MembershipPaymentException(MembershipPaymentError.ORDER_CONFLICT);
        }
        if (!"PENDING".equals(order.get("status"))
                || !payment.channel().equals(order.get("pay_channel"))
                || payment.amount().compareTo((BigDecimal) order.get("amount")) != 0) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        }
        long memberId = ((Number) order.get("member_id")).longValue();
        // Serialize purchases for the same member, including full-price upgrades.
        jdbc.queryForObject("SELECT id FROM ums_member WHERE id=? FOR UPDATE", Long.class, memberId);
        UmsMemberIdentity identity = identities.getIdentity(memberId);
        int fromType = identity == null || identity.getIdentityType() == null ? 0 : identity.getIdentityType();
        String level = (String) order.get("level");
        int toType = "PARTNER".equals(level) ? 1 : "COFOUNDER".equals(level) ? 2 : -1;
        if (toType < 0 || fromType >= toType) {
            throw new MembershipPaymentException(MembershipPaymentError.ALREADY_MEMBER);
        }
        int updated = jdbc.update("UPDATE membership_purchase_order"
                        + " SET status='PAID',transaction_id=?,paid_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",
                payment.transactionId(), orderId);
        if (updated != 1) throw new MembershipPaymentException(MembershipPaymentError.ORDER_CONFLICT);

        long productPoints = ((Number) order.get("product_points")).longValue();
        long repurchasePoints = ((Number) order.get("repurchase_points")).longValue();
        creditPoints(memberId, orderId, 1, "PRODUCT", productPoints);
        creditPoints(memberId, orderId, 2, "REPURCHASE", repurchasePoints);
        CommonResult<Void> changed = identities.changeIdentity(memberId, toType, null,
                "已验签会员权益支付，订单 " + orderId, orderId);
        if (changed.getCode() != 200) throw new IllegalStateException("会员身份更新失败: " + changed.getMessage());
        String fromLevel = label(fromType);
        String toLevel = label(toType);
        jdbc.update("INSERT INTO member_level_change_log(member_id,order_id,from_level,to_level,`operator`,reason)"
                        + " VALUES(?,?,?,?,?,?)",
                memberId, orderId, fromLevel, toLevel, "SYSTEM:payment", "支付订单 " + orderId + " 已验签入账");
        jdbc.update("INSERT INTO sys_audit_log(operator_name,module,action,target_id,detail,create_time,"
                        + "`operator`,target_type,old_value,new_value,created_at)"
                        + " VALUES('SYSTEM:payment','membership','PURCHASE_PAID',?,?,CURRENT_TIMESTAMP,"
                        + "'SYSTEM:payment','membership_purchase_order',?,?,CURRENT_TIMESTAMP)",
                orderId, "会员 " + memberId + " 购买 " + level + "，渠道 " + payment.channel(),
                "status=PENDING;level=" + fromLevel,
                "status=PAID;level=" + toLevel + ";productPoints=+" + productPoints
                        + ";repurchasePoints=+" + repurchasePoints);
        // P0-1e：直推奖励结算（同一事务；无上级/已结算则跳过）
        settleReferralReward(memberId, orderId, level, (BigDecimal) order.get("amount"));
    }

    private void creditPoints(long memberId, long orderId, int walletType,
                              String ledgerType, long amount) {
        // 配置为 0 表示不赠送该积分项，直接跳过（不抛异常，避免整单回滚）
        if (amount <= 0) return;
        String idempotency = "membership:" + orderId + ":" + walletType;
        CommonResult<Void> result = pointsWallet.changePoints(memberId, walletType, "earn", amount,
                Long.toString(orderId), idempotency, null, "会员权益支付赠送");
        if (result.getCode() != 200) throw new IllegalStateException("积分入账失败: " + result.getMessage());
        // 积分只走 PointsWalletService 单写路径（ums_points_account + ums_points_ledger）。
        // member_point_ledger 新表暂时闲置，待对账方案确定后再决定是否启用。
    }

    private String label(int type) {
        return type == 1 ? "PARTNER" : type == 2 ? "COFOUNDER" : "NORMAL";
    }

    /**
     * P0-1e 直推奖励结算（与支付成功链路同一事务）。
     * 无上级/上级是自己/该订单已结算过 → 直接跳过，保证幂等。
     */
    private void settleReferralReward(long payerId, long orderId, String level, BigDecimal paidAmount) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT inviter_id FROM ums_member_recommend WHERE member_id=? LIMIT 1", payerId);
        if (rows.isEmpty() || rows.get(0).get("inviter_id") == null) return;
        long inviterId = ((Number) rows.get(0).get("inviter_id")).longValue();
        if (inviterId <= 0 || inviterId == payerId) return;

        List<Map<String, Object>> done = jdbc.queryForList(
                "SELECT id FROM member_referral_reward WHERE order_id=? LIMIT 1", orderId);
        if (!done.isEmpty()) return;

        String prefix = "COFOUNDER".equals(level) ? "membership.referral.cofounder."
                : "membership.referral.partner.";
        BigDecimal rate = decimal(config("membership.referral.cash_rate"));
        BigDecimal cap = decimalOrNull(config(prefix + "cash_cap"));
        BigDecimal rewardAmount = calculateReferralCash(paidAmount, rate, cap);
        long rewardPoints = longOrZero(config(prefix + "repurchase_points"));

        // 先写奖励记录（order_id 唯一）：并发/重放入口，冲突即视为已结算
        try {
            jdbc.update("INSERT INTO member_referral_reward"
                            + "(member_id,from_member_id,order_id,basis_amount,reward_rate,"
                            + "reward_cap,reward_amount,reward_points,status)"
                            + " VALUES(?,?,?,?,?,?,?,?, 'POSTED')",
                    inviterId, payerId, orderId, paidAmount, rate, cap, rewardAmount, rewardPoints);
        } catch (DuplicateKeyException already) {
            return;
        }

        // 现金部分：记入上级可提现账户（乐观锁，失败抛异常回滚等回调重试）
        if (rewardAmount.signum() > 0) {
            jdbc.update("INSERT IGNORE INTO member_cash_account(member_id,available_balance)"
                    + " VALUES(?,0.00)", inviterId);
            List<Map<String, Object>> acc = jdbc.queryForList(
                    "SELECT available_balance,version FROM member_cash_account WHERE member_id=? LIMIT 1",
                    inviterId);
            long version = ((Number) acc.get(0).get("version")).longValue();
            int updated = jdbc.update("UPDATE member_cash_account"
                            + " SET available_balance=available_balance+?,version=version+1"
                            + " WHERE member_id=? AND version=?",
                    rewardAmount, inviterId, version);
            if (updated != 1) throw new IllegalStateException("直推现金奖励入账并发冲突，等待回调重试");
            BigDecimal after = ((BigDecimal) acc.get(0).get("available_balance")).add(rewardAmount);
            jdbc.update("INSERT INTO member_cash_ledger"
                            + "(member_id,change_amount,balance_after,source_type,source_id)"
                            + " VALUES(?,?,?,'REFERRAL_REWARD',?)",
                    inviterId, rewardAmount, after, orderId);
        }

        // 积分部分：复购积分（points_type=2）
        if (rewardPoints > 0) {
            CommonResult<Void> result = pointsWallet.changePoints(inviterId, 2, "earn", rewardPoints,
                    Long.toString(orderId), "referral:" + orderId, null, "直推奖励");
            if (result.getCode() != 200) throw new IllegalStateException("直推积分入账失败: " + result.getMessage());
        }
    }

    /**
     * 直推现金奖励计算：basis × rate，cap 为 null 表示无上限，结果保留 2 位小数。
     * 例：10000 × 0.10 上限 200 → 200.00；3999 × 0.10 无上限 → 399.90。
     */
    public static BigDecimal calculateReferralCash(BigDecimal basis, BigDecimal rate, BigDecimal cap) {
        BigDecimal reward = basis.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
        if (cap != null && reward.compareTo(cap) > 0) return cap.setScale(2, java.math.RoundingMode.HALF_UP);
        return reward;
    }

    private String config(String key) {
        List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                (rs, n) -> rs.getString(1), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal decimal(String text) {
        try {
            BigDecimal v = new BigDecimal(text);
            if (v.signum() < 0) throw new NumberFormatException("negative");
            return v;
        } catch (Exception ex) {
            throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
        }
    }

    private BigDecimal decimalOrNull(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        return decimal(text);
    }

    private long longOrZero(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try {
            long v = Long.parseLong(text.trim());
            return v < 0 ? 0 : v;
        } catch (Exception ex) {
            return 0;
        }
    }
}
