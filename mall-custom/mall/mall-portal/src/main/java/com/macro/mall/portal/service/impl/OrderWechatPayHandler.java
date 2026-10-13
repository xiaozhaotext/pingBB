package com.macro.mall.portal.service.impl;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.WechatOrderPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把已验签的微信支付结果落到订单上：金额核对、幂等、迟到支付进人工补偿。
 * 与支付宝通知处理保持同一套口径，避免重复入账或漏单。
 */
@Slf4j
@Service
public class OrderWechatPayHandler {

    private static final String CHANNEL = "WECHAT";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private PaymentExceptionRecorder paymentExceptionRecorder;

    @Transactional
    public String handle(WechatOrderPayService.VerifiedPayment payment) {
        OmsOrder order = portalOrderService.getOrderByOrderSn(payment.outTradeNo());
        if (order == null || order.getPayAmount() == null) {
            log.error("微信支付通知找不到订单：{}", payment.outTradeNo());
            return "failure";
        }
        if (order.getPayAmount().compareTo(payment.amount()) != 0) {
            paymentExceptionRecorder.record(order, payment.transactionId(), payment.amount(), "AMOUNT_MISMATCH", CHANNEL);
            log.error("微信支付金额与订单不一致，订单：{}，回调：{}，应付：{}",
                    payment.outTradeNo(), payment.amount(), order.getPayAmount());
            return "failure";
        }
        if (order.getStatus() != null && order.getStatus() >= 1 && order.getStatus() <= 3) {
            Long matching = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oms_payment_confirmation WHERE order_id=? AND channel_code=? AND channel_trade_no=?",
                    Long.class, order.getId(), CHANNEL, payment.transactionId());
            if (matching != null && matching == 1) {
                return "success";
            }
            paymentExceptionRecorder.record(order, payment.transactionId(), order.getPayAmount(),
                    "SECOND_OR_UNMATCHED_PAYMENT", CHANNEL);
            return "success";
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            paymentExceptionRecorder.record(order, payment.transactionId(), order.getPayAmount(),
                    "LATE_PAYMENT_CLOSED_ORDER", CHANNEL);
            log.error("微信迟到支付已进入人工退款补偿队列，orderSn:{}，status:{}", payment.outTradeNo(), order.getStatus());
            return "success";
        }
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO oms_payment_confirmation"
                        + "(order_id,channel_code,channel_trade_no,paid_amount,currency_code) VALUES(?,?,?,?,'CNY')",
                order.getId(), CHANNEL, payment.transactionId(), order.getPayAmount());
        if (inserted != 1) {
            Long matching = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oms_payment_confirmation WHERE order_id=? AND channel_code=? AND channel_trade_no=?",
                    Long.class, order.getId(), CHANNEL, payment.transactionId());
            if (matching != null && matching == 1) {
                return "success";
            }
            paymentExceptionRecorder.record(order, payment.transactionId(), order.getPayAmount(),
                    "DUPLICATE_TRADE_OR_ORDER", CHANNEL);
            return "success";
        }
        portalOrderService.paySuccessByOrderSn(payment.outTradeNo(), 2);
        return "success";
    }
}