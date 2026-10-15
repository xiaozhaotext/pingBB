package com.macro.mall.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mall.recharge")
public class RechargeProperties {
    private final MembershipPaymentProperties payment = new MembershipPaymentProperties();
}
