package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会员身份变更审计日志
 */
@Data
public class UmsMemberIdentityLog implements Serializable {
    private Long id;
    private Long memberId;
    private Integer fromType;
    private Integer toType;
    private String reason;
    private Long operatorId;
    private Long bizOrderId;
    private Date createTime;
}
