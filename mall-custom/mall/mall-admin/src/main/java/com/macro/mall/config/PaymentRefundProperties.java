package com.macro.mall.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 渠道退款开关。
 * 默认关闭：未配置渠道凭证或未开启时，售后只用"待人工退款"如实登记，绝不谎报"已退款"。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mall.payment.refund")
public class PaymentRefundProperties {
    /** 是否启用真实渠道退款 */
    private boolean enabled = false;
    /** 允许退款的渠道列表，默认仅支付宝 */
    private List<String> channels = List.of("ALIPAY");
}