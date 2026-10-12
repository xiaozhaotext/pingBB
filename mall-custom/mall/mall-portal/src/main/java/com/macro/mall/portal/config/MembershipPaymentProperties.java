package com.macro.mall.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Credentials are supplied by the deployment environment, never by source defaults. */
@Data
@Component
@ConfigurationProperties(prefix = "mall.membership.payment")
public class MembershipPaymentProperties {
    private boolean enabled;
    private final Wechat wechat = new Wechat();
    private final Alipay alipay = new Alipay();

    @Data
    public static class Wechat {
        private boolean enabled;
        private String appId;
        private String merchantId;
        private String merchantSerialNumber;
        private String privateKeyPath;
        private String apiV3Key;
        private String notifyUrl;
    }

    @Data
    public static class Alipay {
        private boolean enabled;
        private String appId;
        private String sellerId;
        private String privateKeyPath;
        private String publicKeyPath;
        private String gatewayUrl;
        private String notifyUrl;
        private String returnUrl;
    }
}
