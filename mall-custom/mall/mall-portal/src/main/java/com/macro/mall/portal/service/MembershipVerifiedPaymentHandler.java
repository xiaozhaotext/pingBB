package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.domain.UmsPointsAccount;
import com.macro.mall.portal.domain.UmsMemberIdentity;
import com.macro.mall.common.api.CommonResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Durable, repeatable fulfillment of a provider-verified membership payment. */
@Service
public class MembershipVerifiedPaymentHandler {
    private final JdbcTemplate jdbc;
    private final PointsWalletService pointsWallet;
    private final MemberIdentityService identities;
    private final MembershipReferralRewardService referralRewards;
    @org.springframework.beans.factory.annotation.Autowired private TriMembershipPurchaseService triPurchases;
    @org.springframework.beans.factory.annotation.Value("${mall.membership-v2.enabled:false}") private boolean membershipV2Enabled;

    public MembershipVerifiedPaymentHandler(JdbcTemplate jdbc, PointsWalletService pointsWallet,
                                            MemberIdentityService identities,
                                            MembershipReferralRewardService referralRewards) {
        this.jdbc = jdbc;
        this.pointsWallet = pointsWallet;
        this.identities = identities;
        this.referralRewards = referralRewards;
    }

    @Transactional(rollbackFor = Exception.class)
    public void accept(MembershipPaymentGateway.VerifiedPayment payment) {
        if (payment == null || payment.amount() == null || payment.amount().signum() <= 0
                || payment.merchantOrderNo() == null || payment.transactionId() == null) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,member_id,level,amount,product_points,repurchase_points,pay_channel,status,transaction_id,"
                        + "referrer_member_id,referral_cash_rate,referral_cash_cap,referral_repurchase_points"
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
        if ("MEMBER".equals(order.get("level"))) {
            if (!membershipV2Enabled) throw new IllegalStateException("新会员规则尚未启用");
            triPurchases.fulfill(order);
            if (jdbc.update("UPDATE membership_purchase_order SET status='PAID',transaction_id=?,paid_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",payment.transactionId(),orderId)!=1) throw new IllegalStateException("会员支付状态冲突");
            return;
        }
        if (membershipV2Enabled) throw new IllegalStateException("旧会员订单需先核对后处理");
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
        Number referrer = (Number) order.get("referrer_member_id");
        Number referralPoints = (Number) order.get("referral_repurchase_points");
        referralRewards.post(orderId, memberId, level, payment.amount(),
                referrer == null ? null : referrer.longValue(),
                (BigDecimal) order.get("referral_cash_rate"),
                (BigDecimal) order.get("referral_cash_cap"),
                referralPoints == null ? null : referralPoints.longValue());
        fundDividendPool(orderId, payment.amount());
    }

    /** Fund the dividend pool with a configurable portion of the membership purchase amount. */
    private void fundDividendPool(long orderId, BigDecimal paidAmount) {
        BigDecimal ratio = configDecimal("dividend.pool.ratio", new BigDecimal("0.10"));
        if (ratio.signum() <= 0 || paidAmount.signum() <= 0) return;
        BigDecimal contribution = paidAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        if (contribution.signum() <= 0) return;
        // Idempotent: unique source prevents double funding on retry.
        int inserted = jdbc.update("INSERT IGNORE INTO dividend_pool_ledger"
                        + "(change_amount,balance_after,source_type,source_id,remark)"
                        + " SELECT ?,pool_balance+?, 'MEMBERSHIP_PURCHASE', ?, ? FROM dividend_pool WHERE id=1",
                contribution, contribution, orderId, "会员订单 " + orderId + " 按 " + ratio + " 注入分红池");
        if (inserted == 1) {
            jdbc.update("UPDATE dividend_pool SET pool_balance=pool_balance+?,total_in=total_in+? WHERE id=1",
                    contribution, contribution);
        }
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

    private void creditPoints(long memberId, long orderId, int walletType,
                              String ledgerType, long amount) {
        if (amount < 0) throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
        if (amount == 0) return;
        String idempotency = "membership:" + orderId + ":" + walletType;
        CommonResult<Void> result = pointsWallet.changePoints(memberId, walletType, "earn", amount,
                Long.toString(orderId), idempotency, null, "会员权益支付赠送");
        if (result.getCode() != 200) throw new IllegalStateException("积分入账失败: " + result.getMessage());
        UmsPointsAccount account = pointsWallet.getAccount(memberId, walletType);
        if (account == null || account.getBalance() == null) {
            throw new IllegalStateException("积分余额读取失败");
        }
        jdbc.update("INSERT INTO member_point_ledger"
                        + "(member_id,point_type,change_amount,balance_after,source_type,source_id)"
                        + " VALUES(?,?,?,?,'PURCHASE_GRANT',?)",
                memberId, ledgerType, amount, account.getBalance(), orderId);
        // 产品积分同步到 ums_member.integration，复用下单时的 useIntegration 抵扣链路（等额抵扣）
        if (walletType == 1) {
            jdbc.update("UPDATE ums_member SET integration=integration+? WHERE id=?", amount, memberId);
        }
    }

    private String label(int type) {
        return type == 1 ? "PARTNER" : type == 2 ? "COFOUNDER" : "NORMAL";
    }
}
