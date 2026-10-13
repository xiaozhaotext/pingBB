package com.macro.mall.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 普通订单支付/退款所用的支付宝渠道凭证。
 * 凭证由部署环境通过 alipay.* 注入，源码不含任何默认密钥；缺失时退款会被安全跳过。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayChannelProperties {
    /** 支付宝网关地址，正式环境为 https://openapi.alipay.com/gateway.do */
    private String gatewayUrl;
    /** 应用ID */
    private String appId;
    /** 应用私钥 */
    private String appPrivateKey;
    /** 支付宝公钥 */
    private String alipayPublicKey;
    /** 参数返回格式，只支持JSON */
    private String format = "JSON";
    /** 请求使用的编码格式 */
    private String charset = "UTF-8";
    /** 签名算法类型 */
    private String signType = "RSA2";
}