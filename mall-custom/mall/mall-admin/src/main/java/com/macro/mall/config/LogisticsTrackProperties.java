package com.macro.mall.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 承运商物流轨迹查询配置（当前支持快递100实时查询）。
 * 凭证由部署环境注入；未配置或未启用时接口如实返回"未接入"，不会伪造轨迹。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mall.logistics")
public class LogisticsTrackProperties {
    /** 是否启用真实物流轨迹查询 */
    private boolean enabled = false;
    /** 查询服务地址（快递100实时查询） */
    private String url = "https://poll.kuaidi100.com/poll/query.do";
    /** 快递100 customer（授权账号） */
    private String customer;
    /** 快递100 授权 key，用于 MD5 签名 */
    private String key;
    /** 超时时间（毫秒） */
    private int timeoutMillis = 5000;
}