package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsMemberIdentity;

/**
 * 会员身份服务（普通会员=0 / 合伙人=1 / 联创=2）
 * 后台身份调整必须写变更审计。
 */
public interface MemberIdentityService {

    int TYPE_ORDINARY = 0;
    int TYPE_PARTNER = 1;
    int TYPE_COFOLLOWER = 2;

    UmsMemberIdentity getIdentity(Long memberId);

    /** 变更会员身份（插入/更新当前身份 + 写审计日志） */
    CommonResult<Void> changeIdentity(Long memberId, int newType, Long operatorId, String reason, Long bizOrderId);
}
