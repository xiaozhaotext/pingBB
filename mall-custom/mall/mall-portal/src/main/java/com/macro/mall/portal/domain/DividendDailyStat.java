package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 每日分红池-每日统计（按会员/日期聚合）
 */
@Data
public class DividendDailyStat implements Serializable {
    private Long id;
    private Date statDate;
    private Long memberId;
    private BigDecimal contributionValue;
    private Integer orderCount;
    private BigDecimal orderAmount;
    private Date createTime;
}
