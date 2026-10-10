package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsInvitationCode;
import com.macro.mall.portal.domain.UmsMemberRecommend;

import java.util.List;

/**
 * 邀请码与推荐关系服务
 * 约束：每人唯一邀请码；推荐关系防重复绑定、防循环、防会员自改；绑定留审计。
 */
public interface InvitationService {

    /** 获取或生成会员唯一邀请码 */
    CommonResult<String> ensureCode(Long memberId);

    UmsInvitationCode getMyCode(Long memberId);

    /** 通过邀请码绑定推荐关系 */
    CommonResult<UmsMemberRecommend> bind(Long inviteeId, String code, Long operatorId);

    /** 查询我的上级（推荐人） */
    UmsMemberRecommend getMyInviter(Long memberId);

    /** 查询我的下级列表 */
    List<UmsMemberRecommend> listMyInvitees(Long memberId);
}
