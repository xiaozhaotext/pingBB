package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.BizPartnerConfig;
import com.macro.mall.portal.domain.BizPartnerOrder;

/**
 * 合伙人体系（模拟）
 * 仅实现：方案展示、模拟下单、模拟支付->身份升级+赠送双积分。
 * 不启用真实资金、真实支付或自动奖励发放。
 */
public interface PartnerService {

    BizPartnerConfig getConfig();

    /** 创建模拟购买订单（幂等键 bizId 防重复下单） */
    CommonResult<BizPartnerOrder> createSimulateOrder(Long memberId, String bizId);

    /** 模拟支付成功 -> 身份升级为合伙人 + 赠送双积分（事务+幂等） */
    CommonResult<Void> confirmSimulatePay(Long memberId, String bizId);
}
