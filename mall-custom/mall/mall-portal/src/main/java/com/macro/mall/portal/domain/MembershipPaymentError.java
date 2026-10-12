package com.macro.mall.portal.domain;

import com.macro.mall.common.api.IErrorCode;

/** Stable business errors for membership payment requests. */
public enum MembershipPaymentError implements IErrorCode {
    INVALID_REQUEST(56001, "购买参数不合法"),
    CHANNEL_UNAVAILABLE(56002, "支付渠道尚未完成配置或未启用"),
    RULE_UNAVAILABLE(56003, "会员购买规则尚未启用"),
    ALREADY_MEMBER(56004, "当前身份不能重复购买该权益"),
    ORDER_CONFLICT(56005, "请求号已用于另一笔会员购买"),
    INVALID_NOTIFICATION(56006, "支付通知验签或交易信息校验失败"),
    SETTLEMENT_UNAVAILABLE(56007, "支付入账服务尚未启用，请稍后重试通知"),
    PROVIDER_ERROR(56008, "支付渠道暂时无法下单，请稍后重试");

    private final long code;
    private final String message;

    MembershipPaymentError(long code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public long getCode() { return code; }
    @Override public String getMessage() { return message; }
}
