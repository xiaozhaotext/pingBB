package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 提现申请（模拟状态流转，无真实打款）
 * 状态：0->申请; 1->审核通过; 2->模拟处理中; 3->完成; 4->拒绝
 */
@Data
public class UmsWithdrawApply implements Serializable {
    private Long id;
    private Long memberId;
    private String idempotencyKey;
    private BigDecimal amount;       // 申请金额
    private BigDecimal feeRate;      // 手续费率(默认0.6%,待审批)
    private BigDecimal feeAmount;    // 手续费(模拟计算)
    private BigDecimal actualAmount; // 实际到账(模拟)
    private Integer status;
    private Long auditId;
    private String auditRemark;
    private Date applyTime;
    private Date auditTime;
    private Integer simulateFlag;    // 1->模拟,无真实打款
    private Date createTime;
}
