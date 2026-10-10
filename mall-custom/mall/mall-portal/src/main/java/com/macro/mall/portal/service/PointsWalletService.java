package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsPointsAccount;
import com.macro.mall.portal.domain.UmsPointsLedger;

import java.util.List;

/**
 * 双积分钱包服务（商品积分=1 / 复购积分=2）
 * 关键约束：余额变动必须写流水；幂等；乐观锁防并发；事务一致；禁止负余额。
 */
public interface PointsWalletService {

    int TYPE_PRODUCT = 1;     // 商品积分
    int TYPE_REPURCHASE = 2;  // 复购积分

    UmsPointsAccount getAccount(Long memberId, int pointsType);

    List<UmsPointsLedger> getLedger(Long memberId, Integer pointsType);

    /**
     * 积分变动（earn/consume/adjust/dividend）。
     * @param idempotencyKey 幂等键，防重复发放
     */
    CommonResult<Void> changePoints(Long memberId, int pointsType, String changeType,
                                    long amount, String bizId, String idempotencyKey,
                                    Long operatorId, String remark);

    /** 冻结积分（可用->冻结） */
    CommonResult<Void> freeze(Long memberId, int pointsType, long amount, String idempotencyKey, Long operatorId, String remark);

    /** 解冻积分（冻结->可用） */
    CommonResult<Void> unfreeze(Long memberId, int pointsType, long amount, String idempotencyKey, Long operatorId, String remark);
}
