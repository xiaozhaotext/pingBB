package com.macro.mall.portal.service.impl;

import com.macro.mall.model.OmsOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Durable review queue for verified charges that cannot be applied to the order. */
@Service
public class PaymentExceptionRecorder {
    @Autowired private JdbcTemplate jdbc;

    public void record(OmsOrder order, String tradeNo, BigDecimal amount, String reason) {
        jdbc.update("INSERT INTO oms_payment_exception(order_id,order_sn,channel_code,channel_trade_no,paid_amount,currency_code,reason,state) "
                        + "VALUES(?,?,'ALIPAY',?,?,'CNY',?,'OPEN') ON DUPLICATE KEY UPDATE id=id",
                order.getId(), order.getOrderSn(), tradeNo, amount, reason);
        Long exceptionId = jdbc.queryForObject(
                "SELECT id FROM oms_payment_exception WHERE channel_code='ALIPAY' AND channel_trade_no=? AND order_id=?",
                Long.class, tradeNo, order.getId());
        if (exceptionId == null) {
            throw new IllegalStateException("Payment trade number belongs to another order");
        }
        jdbc.update("INSERT INTO oms_refund_compensation(payment_exception_id,state) "
                + "VALUES(?,'WAITING_MANUAL_REVIEW') ON DUPLICATE KEY UPDATE id=id", exceptionId);
    }
}
