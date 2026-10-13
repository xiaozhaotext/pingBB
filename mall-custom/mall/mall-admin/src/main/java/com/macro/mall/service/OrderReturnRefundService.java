package com.macro.mall.service;

import com.macro.mall.service.PaymentRefundService.RefundOutcome;
import com.macro.mall.service.PaymentRefundService.RefundRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 退货完成时的渠道退款编排：读取支付确认、调用渠道退款、登记退款记录。
 * 退款结果如实写入 oms_refund_record 并返回可写入订单操作历史的说明，
 * 无论成功、失败还是渠道未启用，都不会谎报"已退款"。
 */
@Slf4j
@Service
public class OrderReturnRefundService {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PaymentRefundService paymentRefundService;

    public String refundForReturn(Long returnApplyId, Long orderId, BigDecimal returnAmount, String reason) {
        if (returnApplyId == null || orderId == null || returnAmount == null) {
            return "退货完成，库存已归还；退款参数不完整，请人工核对退款";
        }
        // 退款请求号以退货单为准，重复执行不会在渠道侧产生二次退款。
        String outRequestNo = "RA" + returnApplyId;
        Map<String, Object> payment;
        try {
            payment = jdbc.queryForMap(
                    "SELECT channel_code,channel_trade_no FROM oms_payment_confirmation WHERE order_id=?", orderId);
        } catch (EmptyResultDataAccessException e) {
            payment = null;
        }
        if (payment == null) {
            record(returnApplyId, orderId, "UNKNOWN", null, outRequestNo, returnAmount, "SKIPPED", null,
                    "NO_PAYMENT_CONFIRMATION");
            return "退货完成，库存已归还；未查到渠道支付记录，未发起退款，请人工核对";
        }
        String channelCode = payment.get("channel_code") == null ? "UNKNOWN" : String.valueOf(payment.get("channel_code"));
        String channelTradeNo = payment.get("channel_trade_no") == null ? null : String.valueOf(payment.get("channel_trade_no"));
        String orderSn = jdbc.queryForObject("SELECT order_sn FROM oms_order WHERE id=?", String.class, orderId);
        RefundOutcome outcome = paymentRefundService.refund(
                new RefundRequest(channelCode, orderSn, channelTradeNo, returnAmount, outRequestNo, reason));
        switch (outcome.state()) {
            case SUCCESS:
                record(returnApplyId, orderId, channelCode, channelTradeNo, outRequestNo, returnAmount, "SUCCESS",
                        outcome.channelRefundNo(), null);
                return "退货完成，库存已归还，渠道退款已受理";
            case FAILED:
                String error = truncate(joinMessage(outcome.errorCode(), outcome.errorMessage()));
                record(returnApplyId, orderId, channelCode, channelTradeNo, outRequestNo, returnAmount, "FAILED", null, error);
                return "退货完成，库存已归还；渠道退款失败（" + error + "），请人工处理";
            default:
                record(returnApplyId, orderId, channelCode, channelTradeNo, outRequestNo, returnAmount, "SKIPPED", null,
                        truncate(outcome.errorCode()));
                return "退货完成，库存已归还；渠道退款未启用（" + outcome.errorCode() + "），待人工退款";
        }
    }

    private void record(Long returnApplyId, Long orderId, String channelCode, String channelTradeNo,
                        String outRequestNo, BigDecimal amount, String state, String channelRefundNo, String errorMsg) {
        jdbc.update("INSERT INTO oms_refund_record"
                        + "(return_apply_id,order_id,channel_code,channel_trade_no,out_request_no,refund_amount,state,channel_refund_no,error_msg)"
                        + " VALUES(?,?,?,?,?,?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE state=VALUES(state),channel_refund_no=VALUES(channel_refund_no),"
                        + "error_msg=VALUES(error_msg)",
                returnApplyId, orderId, channelCode, channelTradeNo, outRequestNo, amount, state, channelRefundNo, errorMsg);
    }

    private static String joinMessage(String code, String message) {
        if (code == null) {
            return message == null ? "未知错误" : message;
        }
        return message == null ? code : code + ":" + message;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}