package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 推荐关系绑定审计（防止会员自行修改推荐人）
 */
@Data
public class UmsMemberRecommendAudit implements Serializable {
    private Long id;
    private Long memberId;
    private Long inviterId;
    private String inviteCode;
    private String action; // bind/reject/cycle_blocked/self_blocked
    private Long operatorId;
    private String detail;
    private Date createTime;
}
