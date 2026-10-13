package com.macro.mall.service;

import java.math.BigDecimal;

/**
 * 支付渠道退款能力。
 * 返回结果用于在售后单与操作历史中如实标注退款状态，避免"未退款却写已退款"。
 */
public interface PaymentRefundService {

    RefundOutcome refund(RefundRequest request);

    /** 一次渠道退款请求。outRequestNo 用于渠道侧幂等，必须全局唯一。 */
    record RefundRequest(String channelCode, String outTradeNo, String channelTradeNo,
                         BigDecimal amount, String outRequestNo, String reason) {
    }

    record RefundOutcome(State state, String channelRefundNo, String errorCode, String errorMessage) {

        public enum State {
            /** 渠道受理并完成退款 */
            SUCCESS,
            /** 渠道明确拒绝或调用失败，需要人工介入 */
            FAILED,
            /** 渠道未启用/凭证缺失/渠道不支持，未发起退款 */
            SKIPPED
        }

        public static RefundOutcome success(String channelRefundNo) {
            return new RefundOutcome(State.SUCCESS, channelRefundNo, null, null);
        }

        public static RefundOutcome failed(String errorCode, String errorMessage) {
            return new RefundOutcome(State.FAILED, null, errorCode, errorMessage);
        }

        public static RefundOutcome skipped(String errorCode) {
            return new RefundOutcome(State.SKIPPED, null, errorCode, null);
        }

        public boolean isSuccess() {
            return state == State.SUCCESS;
        }
    }
}