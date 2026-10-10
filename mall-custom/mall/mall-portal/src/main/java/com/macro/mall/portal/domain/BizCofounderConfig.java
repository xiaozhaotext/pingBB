package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 联创权益配置（规则草案；一切金额/分红均为模拟，不启用真实资金）
 */
@Data
public class BizCofounderConfig implements Serializable {
    private Long id;
    private BigDecimal price;            // 权益价格(元,模拟) 默认10000
    private Integer cofounderPoints;     // 联创积分
    private BigDecimal shareRewardCap;   // 分享奖励上限(200元)
    private Integer dividendCount;       // 三份分红
    private Integer simulateFlag;       // 1->模拟
    private Integer enabled;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
