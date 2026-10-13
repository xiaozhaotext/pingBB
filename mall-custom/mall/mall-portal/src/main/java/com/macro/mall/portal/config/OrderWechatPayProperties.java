package com.macro.mall.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 普通订单微信支付渠道凭证（H5 + JSAPI）。
 * 凭证由部署环境注入，源码不含默认密钥；未配置时渠道不可用，接口按开关返回 503。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mall.payment.wechat")
public class OrderWechatPayProperties {
    /** 是否启用普通订单微信支付 */
    private boolean enabled = false;
    /** 公众号/小程序 appId */
    private String appId;
    /** 微信支付商户号 */
    private String merchantId;
    /** 商户证书序列号 */
    private String merchantSerialNumber;
    /** 商户私钥文件路径（apiclient_key.pem） */
    private String privateKeyPath;
    /** APIv3 密钥 */
    private String apiV3Key;
    /** 支付结果通知地址（必须公网可访问且备案） */
    private String notifyUrl;
    /** H5 支付完成后跳转地址 */
    private String returnUrl;
}