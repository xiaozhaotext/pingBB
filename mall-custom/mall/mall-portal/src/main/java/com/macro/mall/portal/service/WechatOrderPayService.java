package com.macro.mall.portal.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 普通订单微信支付渠道（H5 / JSAPI）。
 * 渠道不可用时抛出 503，绝不返回伪造的预支付参数。
 */
public interface WechatOrderPayService {

    /** JSAPI 调起支付所需的签名参数 */
    Map<String, String> prepayJsapi(String outTradeNo, BigDecimal amount, String description, String openId);

    /** H5 支付跳转链接 */
    Map<String, String> prepayH5(String outTradeNo, BigDecimal amount, String description, String clientIp);

    /** 校验并解析微信支付结果通知 */
    VerifiedPayment verifyNotify(String rawBody, String serial, String nonce, String signature, String timestamp);

    record VerifiedPayment(String outTradeNo, String transactionId, BigDecimal amount) {
    }
}