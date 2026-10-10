package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 每日分红池-模拟分配明细
 */
@Data
public class DividendAllocateDetail implements Serializable {
    private Long id;
    private Date statDate;
    private Long memberId;
    private BigDecimal ratio;
    private BigDecimal estimatedAmount; // 模拟分配金额(非真实)
    private Integer simulateFlag;
    private Date createTime;
}
