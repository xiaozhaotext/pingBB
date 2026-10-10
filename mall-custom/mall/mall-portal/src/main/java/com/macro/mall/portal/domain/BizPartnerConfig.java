package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 合伙人权益配置（规则草案，可配置；一切金额/奖励均为模拟，不启用真实资金）
 */
@Data
public class BizPartnerConfig implements Serializable {
    private Long id;
    private BigDecimal price;             // 权益价格(元,演示/模拟) 默认3999
    private Integer productPoints;        // 赠送商品积分
    private Integer repurchasePoints;     // 赠送复购积分
    private BigDecimal dailyDividendRate; // 每日分红比例(草案,待确认)
    private BigDecimal directShareRate;   // 直接分享奖励比例(10%)
    private Integer simulateFlag;        // 1->模拟
    private Integer enabled;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
