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
    }

    private void creditPoints(long memberId, long orderId, int walletType,
                              String ledgerType, long amount) {
        if (amount <= 0) throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
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
    }

    private String label(int type) {
        return type == 1 ? "PARTNER" : type == 2 ? "COFOUNDER" : "NORMAL";
    }
}
