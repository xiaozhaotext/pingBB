package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.domain.MembershipPurchaseRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates a price/point snapshot before requesting real payment parameters. */
@Service
public class MembershipPurchaseOrderService {
    private final JdbcTemplate jdbc;
    private final MemberIdentityService identityService;
    private final MembershipPaymentGateway gateway;
    private final MembershipReferralRewardService referralRewards;
    private final boolean mockPaymentEnabled;

    public MembershipPurchaseOrderService(JdbcTemplate jdbc, MemberIdentityService identityService,
                                          MembershipPaymentGateway gateway,
                                          MembershipReferralRewardService referralRewards,
                                          @Value("${mall.payment.mock.enabled:false}") boolean mockPaymentEnabled) {
        this.jdbc = jdbc;
        this.identityService = identityService;
        this.gateway = gateway;
        this.referralRewards = referralRewards;
        this.mockPaymentEnabled = mockPaymentEnabled;
    }

    public Map<String, Object> create(Long authenticatedMemberId, MembershipPurchaseRequest request, String clientIp) {
        validate(authenticatedMemberId, request);
        if (!"1".equals(config("membership.purchase.enabled"))) {
            throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
        }
        // 本地演示支付桩：跳过真实渠道凭证校验，改由本机 /mockPayment/membership/confirm 完成入账。
        if (!mockPaymentEnabled) gateway.requireReady(request.payChannel());
        Integer identity = identityService.getIdentity(authenticatedMemberId) == null ? 0
                : identityService.getIdentity(authenticatedMemberId).getIdentityType();
        if (identity != null && (identity >= 2 || (identity >= 1 && "PARTNER".equals(request.level())))) {
            throw new MembershipPaymentException(MembershipPaymentError.ALREADY_MEMBER);
        }
        Map<String, Object> order = find(authenticatedMemberId, request.requestId());
        if (order == null) {
            String prefix = "PARTNER".equals(request.level()) ? "membership.partner." : "membership.cofounder.";
            BigDecimal price = decimal(config(prefix + "price"));
            long productPoints = nonNegativeLong(config(prefix + "product_points"));
            long repurchasePoints = nonNegativeLong(config(prefix + "repurchase_points"));
            if (price.signum() <= 0 || price.scale() > 2) {
                throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
            }
            MembershipReferralRewardService.Snapshot reward =
                    referralRewards.snapshotFor(authenticatedMemberId, request.level());
            String merchantNo = "MP" + UUID.randomUUID().toString().replace("-", "");
            try {
                jdbc.update("INSERT INTO membership_purchase_order"
                                + "(merchant_order_no,request_id,member_id,level,amount,product_points,repurchase_points,"
                                + "pay_channel,pay_mode,referrer_member_id,referral_cash_rate,referral_cash_cap,"
                                + "referral_repurchase_points,status)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING')",
                        merchantNo, request.requestId(), authenticatedMemberId, request.level(), price,
                        productPoints, repurchasePoints, request.payChannel(), request.payMode(),
                        reward == null ? null : reward.referrerMemberId(),
                        reward == null ? null : reward.cashRate(),
                        reward == null ? null : reward.cashCap(),
                        reward == null ? null : reward.repurchasePoints());
            } catch (DuplicateKeyException ignored) {
                // A concurrent retry won the unique (member_id,request_id) race.
            }
            order = find(authenticatedMemberId, request.requestId());
        }
        if (order == null || !request.level().equals(order.get("level"))
                || !request.payChannel().equals(order.get("pay_channel"))
                || !request.payMode().equals(order.get("pay_mode"))
                || !"PENDING".equals(order.get("status"))) {
            throw new MembershipPaymentException(MembershipPaymentError.ORDER_CONFLICT);
        }
        String no = (String) order.get("merchant_order_no");
        BigDecimal price = (BigDecimal) order.get("amount");
        Map<String, String> payment;
        if (mockPaymentEnabled) {
            // 不伪造渠道响应：只返回本地桩的确认入口，由 /mockPayment/membership/confirm 触发履约。
            payment = new LinkedHashMap<>();
            payment.put("mock", "true");
            payment.put("confirmPath",
                    "/mockPayment/membership/confirm?orderId=" + ((Number) order.get("id")).longValue());
        } else {
            payment = gateway.start(no, request.level(), price, request.payChannel(),
                    request.payMode(), request.wechatOpenId(), clientIp);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", ((Number) order.get("id")).longValue());
        result.put("merchantOrderNo", no);
        result.put("amount", price);
        result.put("payChannel", request.payChannel());
        result.put("payMode", request.payMode());
        result.put("payment", payment);
        return result;
    }

    private void validate(Long current, MembershipPurchaseRequest request) {
        if (request == null || current == null || !current.equals(request.memberId())
                || !("PARTNER".equals(request.level()) || "COFOUNDER".equals(request.level()))
                || !("WECHAT".equals(request.payChannel()) || "ALIPAY".equals(request.payChannel()))
                || !(("WECHAT".equals(request.payChannel())
                        && ("WECHAT_H5".equals(request.payMode()) || "WECHAT_JSAPI".equals(request.payMode())))
                     || ("ALIPAY".equals(request.payChannel()) && "ALIPAY_WAP".equals(request.payMode())))
                || !StringUtils.hasText(request.requestId())
                || !request.requestId().matches("[A-Za-z0-9_-]{8,64}")) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_REQUEST);
        }
        if ("WECHAT_JSAPI".equals(request.payMode())
                && !StringUtils.hasText(request.wechatOpenId())) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_REQUEST);
        }
    }

    private Map<String, Object> find(Long memberId, String requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,merchant_order_no,level,amount,pay_channel,pay_mode,status FROM membership_purchase_order"
                        + " WHERE member_id=? AND request_id=? LIMIT 1", memberId, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String config(String key) {
        List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                (rs, n) -> rs.getString(1), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal decimal(String text) {
        try { return new BigDecimal(text); }
        catch (Exception ex) { throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE); }
    }

    /**
     * 积分配额：允许 0（表示不赠送该积分项，下单/入账时跳过），拒绝负数与非法值。
     */
    private long nonNegativeLong(String text) {
        try {
            long value = Long.parseLong(text);
            if (value >= 0) return value;
        } catch (Exception ignored) { }
        throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
    }
}
