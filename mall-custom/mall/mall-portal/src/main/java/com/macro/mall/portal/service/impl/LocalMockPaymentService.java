package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仅本地演示启用，记录 MOCK 渠道确认，不发生任何真实资金流。 */
@Service
@ConditionalOnProperty(name = "mall.payment.mock.enabled", havingValue = "true")
public class LocalMockPaymentService {
    private final OmsOrderMapper orderMapper;
    private final UmsMemberService memberService;
    private final OmsPortalOrderService orderService;
    private final JdbcTemplate jdbcTemplate;

    public LocalMockPaymentService(OmsOrderMapper orderMapper, UmsMemberService memberService,
                                   OmsPortalOrderService orderService, JdbcTemplate jdbcTemplate) {
        this.orderMapper = orderMapper;
        this.memberService = memberService;
        this.orderService = orderService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String confirm(Long orderId) {
        OmsOrder order = orderId == null ? null : orderMapper.selectByPrimaryKey(orderId);
        if (order == null || !memberService.getCurrentMember().getId().equals(order.getMemberId())
                || !Integer.valueOf(0).equals(order.getDeleteStatus())) {
            Asserts.fail("订单不存在或无权操作");
        }
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oms_payment_confirmation WHERE order_id=? AND channel_code='MOCK'",
                Long.class, orderId);
        if (existing != null && existing == 1) {
            if (order.getStatus() != null && order.getStatus() >= 1 && order.getStatus() <= 3) return "ALREADY_CONFIRMED";
            Asserts.fail("模拟支付记录与订单状态不一致，请人工核查");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            Asserts.fail("仅待付款订单可以模拟支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount().signum() < 0) {
            Asserts.fail("订单金额异常");
        }
        jdbcTemplate.update("INSERT INTO oms_payment_confirmation"
                        + "(order_id,channel_code,channel_trade_no,paid_amount,currency_code) VALUES(?,'MOCK',?,?,'CNY')",
                orderId, "LOCAL-MOCK-" + orderId, order.getPayAmount());
        orderService.paySuccess(orderId, 3);
        return "CONFIRMED";
    }
}
