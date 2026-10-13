package com.macro.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.macro.mall.config.AlipayChannelProperties;
import com.macro.mall.config.PaymentRefundProperties;
import com.macro.mall.service.PaymentRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 支付宝渠道退款：调用 alipay.trade.refund，使用 out_request_no 保证渠道侧幂等。
 * 凭证缺失或开关关闭时返回 SKIPPED，由上层如实登记为"待人工退款"。
 */
@Slf4j
@Service
public class AlipayPaymentRefundServiceImpl implements PaymentRefundService {

    private static final String ALIPAY = "ALIPAY";

    @Autowired
    private AlipayChannelProperties channelProperties;
    @Autowired
    private PaymentRefundProperties refundProperties;

    private volatile AlipayClient client;

    @Override
    public RefundOutcome refund(RefundRequest request) {
        if (request == null || StrUtil.isBlank(request.channelCode())) {
            return RefundOutcome.skipped("CHANNEL_UNKNOWN");
        }
        if (!ALIPAY.equalsIgnoreCase(request.channelCode())) {
            return RefundOutcome.skipped("CHANNEL_NOT_SUPPORTED:" + request.channelCode());
        }
        if (!refundProperties.isEnabled() || refundProperties.getChannels() == null
                || refundProperties.getChannels().stream().noneMatch(ALIPAY::equalsIgnoreCase)) {
            return RefundOutcome.skipped("REFUND_CHANNEL_DISABLED");
        }
        if (StrUtil.isBlank(request.outTradeNo()) || StrUtil.isBlank(request.outRequestNo())) {
            return RefundOutcome.failed("INVALID_REQUEST", "订单号与退款请求号不能为空");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return RefundOutcome.failed("INVALID_AMOUNT", "退款金额必须大于 0");
        }
        if (StrUtil.isBlank(channelProperties.getAppId()) || StrUtil.isBlank(channelProperties.getAppPrivateKey())
                || StrUtil.isBlank(channelProperties.getAlipayPublicKey())
                || StrUtil.isBlank(channelProperties.getGatewayUrl())) {
            return RefundOutcome.skipped("ALIPAY_CREDENTIALS_MISSING");
        }
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", request.outTradeNo());
        bizContent.put("refund_amount", request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        bizContent.put("out_request_no", request.outRequestNo());
        if (StrUtil.isNotBlank(request.channelTradeNo())) {
            bizContent.put("trade_no", request.channelTradeNo());
        }
        if (StrUtil.isNotBlank(request.reason())) {
            bizContent.put("refund_reason", request.reason());
        }
        AlipayTradeRefundRequest alipayRequest = new AlipayTradeRefundRequest();
        alipayRequest.setBizContent(bizContent.toString());
        try {
            AlipayTradeRefundResponse response = client().execute(alipayRequest);
            if (response.isSuccess()) {
                return RefundOutcome.success(response.getTradeNo());
            }
            log.error("支付宝退款失败，订单：{}，退款请求号：{}，code：{}，msg：{}",
                    request.outTradeNo(), request.outRequestNo(), response.getSubCode(), response.getSubMsg());
            return RefundOutcome.failed(response.getSubCode(), response.getSubMsg());
        } catch (AlipayApiException e) {
            log.error("支付宝退款调用异常，订单：{}，退款请求号：{}", request.outTradeNo(), request.outRequestNo(), e);
            return RefundOutcome.failed("ALIPAY_API_EXCEPTION", e.getMessage());
        }
    }

    private AlipayClient client() {
        AlipayClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new DefaultAlipayClient(channelProperties.getGatewayUrl(), channelProperties.getAppId(),
                            channelProperties.getAppPrivateKey(), channelProperties.getFormat(),
                            channelProperties.getCharset(), channelProperties.getAlipayPublicKey(),
                            channelProperties.getSignType());
                    client = current;
                }
            }
        }
        return current;
    }
}