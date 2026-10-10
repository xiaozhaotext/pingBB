package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会员邀请码（一人一码，唯一，来自真实数据库）
 */
@Data
public class UmsInvitationCode implements Serializable {
    private Long id;
    private Long memberId;
    private String code;
    private String qrContent;
    private String shortLink;
    private Integer status; // 1有效 0失效
    private Date createTime;
}
