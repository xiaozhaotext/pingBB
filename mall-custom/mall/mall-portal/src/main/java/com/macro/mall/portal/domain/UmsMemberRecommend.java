package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 会员推荐关系（每个会员仅能被绑定一次；bind_path 用于防循环）
 */
@Data
public class UmsMemberRecommend implements Serializable {
    private Long id;
    private Long memberId;     // 被推荐人(下级)
    private Long inviterId;    // 推荐人(上级)
    private String inviteCode;
    private Long bindOrderId;
    private String bindPath;   // 如 /1/3/5
    private Date bindTime;
    private Date createTime;
    /** Current business identity, independent of the account identifier memberId. */
    private String membershipTier;
}
