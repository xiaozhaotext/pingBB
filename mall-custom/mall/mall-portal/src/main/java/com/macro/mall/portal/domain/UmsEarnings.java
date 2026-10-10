package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员收益账户（模拟金额，非真实资产）
 */
@Data
public class UmsEarnings implements Serializable {
    private Long id;
    private Long memberId;
    private BigDecimal totalEarnings;     // 累计收益(模拟)
    private BigDecimal availableEarnings; // 可提现(模拟)
    private BigDecimal frozenEarnings;    // 冻结(模拟)
    private BigDecimal withdrawn;         // 已提现(模拟)
    private Long version;                 // 乐观锁
    private Date createTime;
    private Date updateTime;
}
