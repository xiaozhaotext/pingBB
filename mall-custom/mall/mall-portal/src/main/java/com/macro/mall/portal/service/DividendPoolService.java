package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.DividendAllocateDetail;
import com.macro.mall.portal.domain.DividendSnapshot;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 每日分红池（多劳多得 / 基于有效经营贡献值的比例分配）—— 全部为模拟计算。
 * 规则：ratio = 个人贡献 / 当日总贡献；严禁平均分配、严禁写死/随机收益。
 * 不启用真实分红入账/提现/自动付款。
 */
public interface DividendPoolService {

    /** 记录可核验的贡献事件（幂等：同一 biz_id+event_type 不重复计入；退款为负贡献冲回） */
    CommonResult<Void> recordContributionEvent(Long memberId, String eventType, String bizId,
                                               BigDecimal contributionValue, Date statDate);

    /**
     * 模拟每日分配（幂等，按 calcBatch 可复算）。
     * @param statDate    统计日期
     * @param poolTotal   模拟分红资金池（元）；为空时默认等于当日总贡献值
     * @param calcBatch   计算批次（幂等键）
     * @return 参与分配的会员数
     */
    CommonResult<Integer> simulateDailyAllocate(Date statDate, BigDecimal poolTotal, String calcBatch);

    /** 查询某日模拟分配快照 */
    List<DividendSnapshot> getSnapshot(Date statDate);

    /** 查询某日模拟分配明细 */
    List<DividendAllocateDetail> getAllocateDetail(Date statDate);
}
