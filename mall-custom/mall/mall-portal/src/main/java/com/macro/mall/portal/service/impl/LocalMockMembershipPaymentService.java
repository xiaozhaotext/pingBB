package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.service.MembershipPaymentGateway;
import com.macro.mall.portal.service.MembershipVerifiedPaymentHandler;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 会员权益购买的本地支付桩（仅 mall.payment.mock.enabled=true 时注册）。
 *
 * 只负责"渠道已确认收款"这一步的模拟，随后的履约（身份升级、双积分入账、
 * 直推奖励、分红池注资）全部复用 MembershipVerifiedPaymentHandler 的真实逻辑，
 * 保证本地闭环与生产链路是同一套代码，不产生分叉。
 */
@Service
@ConditionalOnProperty(name = "mall.payment.mock.enabled", havingValue = "true")
public class LocalMockMembershipPaymentService {

    private final JdbcTemplate jdbc;
    private final UmsMemberService memberService;
    private final MembershipVerifiedPaymentHandler verifiedPaymentHandler;

    public LocalMockMembershipPaymentService(JdbcTemplate jdbc, UmsMemberService memberService,
                                             MembershipVerifiedPaymentHandler verifiedPaymentHandler) {
        this.jdbc = jdbc;
        this.memberService = memberService;
        this.verifiedPaymentHandler = verifiedPaymentHandler;
    }

    @Transactional(rollbackFor = Exception.class)
    public String confirm(Long orderId) {
        if (orderId == null) Asserts.fail("orderId 不能为空");
        Long memberId = memberService.getCurrentMember().getId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,member_id,merchant_order_no,level,amount,pay_channel,status"
                        + " FROM membership_purchase_order WHERE id=? FOR UPDATE", orderId);
        if (rows.isEmpty()) Asserts.fail("会员订单不存在");
        Map<String, Object> order = rows.get(0);
        if (!memberId.equals(((Number) order.get("member_id")).longValue())) {
            Asserts.fail("无权操作他人的会员订单");
        }
        if ("PAID".equals(order.get("status"))) return "ALREADY_PAID";
        if (!"PENDING".equals(order.get("status"))) Asserts.fail("该会员订单状态不允许支付");

        verifiedPaymentHandler.accept(new MembershipPaymentGateway.VerifiedPayment(
                (String) order.get("merchant_order_no"),
                "LOCAL-MOCK-MP-" + orderId,
                (String) order.get("pay_channel"),
                (BigDecimal) order.get("amount")));
        return "CONFIRMED";
    }
}
