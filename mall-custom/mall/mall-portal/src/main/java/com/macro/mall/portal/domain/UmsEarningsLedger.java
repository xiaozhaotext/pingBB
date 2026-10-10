package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 收益流水（模拟）
 */
@Data
public class UmsEarningsLedger implements Serializable {
    private Long id;
    private Long memberId;
    private String changeType;  // simulate_reward/withdraw_apply/withdraw_done/withdraw_reject
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String bizId;
    private String idempotencyKey;
    private String remark;
    private Date createTime;
}
