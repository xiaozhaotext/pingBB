package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 积分账本流水（所有余额变动必须记录）
 */
@Data
public class UmsPointsLedger implements Serializable {
    private Long id;
    private Long memberId;
    private Integer pointsType;
    private String changeType;   // earn/consume/freeze/unfreeze/adjust/dividend
    private Long amount;         // 正=增, 负=减
    private Long balanceAfter;
    private String bizId;
    private String idempotencyKey; // 幂等键, 防重复发放
    private Long operatorId;
    private String remark;
    private Date createTime;
}
