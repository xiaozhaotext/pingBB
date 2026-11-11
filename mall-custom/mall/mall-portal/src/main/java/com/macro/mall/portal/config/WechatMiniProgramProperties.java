package com.macro.mall.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mall.wechat-mini")
public class WechatMiniProgramProperties {
    private boolean enabled;
    private String appId;
    private String appSecret;
    private String notifyUrl;
}
