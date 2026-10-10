package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会员当前生效身份（普通会员=0 / 合伙人=1 / 联创=2）
 */
@Data
public class UmsMemberIdentity implements Serializable {
    private Long id;
    private Long memberId;
    private Integer identityType;
    private Integer status; // 1生效 0失效
    private Date becomeTime;
    private Date expireTime;
    private Long operatorId;
    private Date createTime;
    private Date updateTime;
}
