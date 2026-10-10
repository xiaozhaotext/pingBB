package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 每日分红池-计算快照（幂等可复算，按 stat_date+member_id+calc_batch 唯一）
 */
@Data
public class DividendSnapshot implements Serializable {
    private Long id;
    private Date statDate;
    private Long memberId;
    private BigDecimal contributionValue;
    private BigDecimal poolTotal;     // 当日总贡献(分母)
    private BigDecimal ratio;         // 分配比例=个人贡献/总贡献
    private BigDecimal estimatedAmount; // 模拟分配金额(非真实)
    private Integer simulateFlag;
    private String calcBatch;         // 计算批次(幂等)
    private Date createTime;
}
