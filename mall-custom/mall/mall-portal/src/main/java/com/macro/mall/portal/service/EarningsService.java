package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsEarnings;
import com.macro.mall.portal.domain.UmsEarningsLedger;
import com.macro.mall.portal.domain.UmsWithdrawApply;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员收益与提现（全部为模拟金额，非真实资产，无真实打款）
 * 提现状态流转：申请(0)->审核通过(1)->模拟处理中(2)->完成(3)/拒绝(4)。
 * 0.6% 手续费为待审批参数；审核/处理由后台执行。
 */
public interface EarningsService {

    UmsEarnings getAccount(Long memberId);

    List<UmsEarningsLedger> getLedger(Long memberId);

    /** 模拟发放收益（幂等，仅用于演示/测试，非真实收入） */
    CommonResult<Void> simulateReward(Long memberId, BigDecimal amount, String bizId, String idempotencyKey, String remark);

    List<UmsWithdrawApply> getWithdraws(Long memberId);

    /** 申请提现（模拟）：扣减可提现、冻结，生成待审核申请 */
    CommonResult<UmsWithdrawApply> applyWithdraw(Long memberId, BigDecimal amount, String idempotencyKey);

    /** 后台审核/处理提现（模拟）：approve=true 完成入账，false 拒绝退回 */
    CommonResult<Void> processWithdraw(Long applyId, Long operatorId, boolean approve, String remark);
}
