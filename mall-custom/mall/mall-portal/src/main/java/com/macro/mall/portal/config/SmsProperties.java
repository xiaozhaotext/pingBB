package com.macro.mall.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信通道配置（阿里云短信 / 通用HTTP短信）。
 * 凭证由部署环境注入；通道未启用时验证码仅在接口返回（仅测试），不会伪造"已发送"。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mall.sms")
public class SmsProperties {
    /** 是否启用真实短信发送 */
    private boolean enabled = false;
    /** 服务商：ALIYUN 或 HTTP */
    private String provider = "ALIYUN";
    /** 短信签名，例如：智游美 */
    private String signName;
    /** 阿里云短信模板编号，模板变量名必须是 code */
    private String templateCode;
    /** 阿里云 AccessKeyId */
    private String accessKeyId;
    /** 阿里云 AccessKeySecret */
    private String accessKeySecret;
    /** 阿里云地域 */
    private String regionId = "cn-hangzhou";
    /** 阿里云短信接口地址 */
    private String endpoint = "https://dysmsapi.aliyuncs.com";
    /** 通用HTTP短信接口地址 */
    private String genericUrl;
    /** 通用HTTP短信请求方式：GET 或 POST */
    private String genericMethod = "GET";
    /** 通用HTTP短信参数模板，支持 ${phone} 与 ${code} 占位符 */
    private String genericParams;
    /** 通用HTTP短信成功关键字，响应包含该字符串视为发送成功；为空时 HTTP 200 即成功 */
    private String genericSuccessKeyword;
    /** 是否在接口响应中回显验证码；仅用于本地联调，生产必须为 false */
    private boolean devReturnCode = false;
    /** 请求超时时间（毫秒） */
    private int timeoutMillis = 5000;
}