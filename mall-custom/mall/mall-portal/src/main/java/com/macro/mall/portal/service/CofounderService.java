package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.BizCofounderConfig;
import com.macro.mall.portal.domain.BizCofounderOrder;

/**
 * 联创体系（模拟）
 * 仅实现：方案展示、模拟下单、模拟支付->身份升级为联创+赠送联创积分。
 * 不启用真实资金、真实支付或真实分红入账。
 */
public interface CofounderService {

    BizCofounderConfig getConfig();

    /** 创建模拟购买订单（幂等键 bizId 防重复下单） */
    CommonResult<BizCofounderOrder> createSimulateOrder(Long memberId, String bizId);

    /** 模拟支付成功 -> 身份升级为联创 + 赠送联创积分（事务+幂等） */
    CommonResult<Void> confirmSimulatePay(Long memberId, String bizId);
}
