package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.*;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.DividendPoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * 每日分红池分配引擎（模拟）。
 * 核心：ratio = 个人贡献 / 当日总贡献，按贡献比例分配模拟资金池。
 * 严禁平均分配、严禁写死/随机收益；退款产生负贡献冲回。
 */
@Service
public class DividendPoolServiceImpl implements DividendPoolService {

    private static final int SCALE = 8;

    @Autowired
    private DividendContributionEventMapper eventMapper;
    @Autowired
    private DividendDailyStatMapper dailyMapper;
    @Autowired
    private DividendSnapshotMapper snapshotMapper;
    @Autowired
    private DividendAllocateDetailMapper allocateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> recordContributionEvent(Long memberId, String eventType, String bizId,
                                                      BigDecimal contributionValue, Date statDate) {
        if (eventMapper.selectByBizEvent(bizId, eventType) != null) {
            return CommonResult.failed("重复计入：该业务单号+事件类型已存在");
        }
        DividendContributionEvent e = new DividendContributionEvent();
        e.setMemberId(memberId);
        e.setEventType(eventType);
        e.setBizId(bizId);
        e.setContributionValue(contributionValue);
        e.setVerifyStatus(1); // 已核验真实业务
        e.setStatDate(statDate);
        eventMapper.insert(e);
        return CommonResult.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Integer> simulateDailyAllocate(Date statDate, BigDecimal poolTotal, String calcBatch) {
        if (calcBatch == null || calcBatch.trim().isEmpty()) {
            return CommonResult.failed("calcBatch 不能为空（幂等键）");
        }
        List<DividendDailyStat> stats = eventMapper.sumByMember(statDate);
        if (stats == null || stats.isEmpty()) {
            return CommonResult.failed("该日期无贡献事件，无法分配");
        }
        // 当日总贡献（分母）
        BigDecimal totalContribution = BigDecimal.ZERO;
        for (DividendDailyStat s : stats) {
            if (s.getContributionValue() == null) s.setContributionValue(BigDecimal.ZERO);
            if (s.getContributionValue().signum() < 0) {
                return CommonResult.failed("会员净贡献不能为负，请先核对退款冲回事件");
            }
            totalContribution = totalContribution.add(s.getContributionValue());
        }
        if (totalContribution.signum() <= 0) {
            return CommonResult.failed("当日有效净贡献必须大于零");
        }
        boolean alreadyAllocated = stats.stream().allMatch(s ->
                snapshotMapper.selectByDateMemberBatch(statDate, s.getMemberId(), calcBatch) != null);
        if (alreadyAllocated) return CommonResult.success(0);
        // 模拟资金池：未提供则默认等于总贡献值（金额单位仅作演示）
        BigDecimal moneyPool = (poolTotal != null && poolTotal.compareTo(BigDecimal.ZERO) > 0)
                ? poolTotal : totalContribution;

        // 维护每日统计（先清后写，保证可复算）
        dailyMapper.deleteByStatDate(statDate);
        for (DividendDailyStat s : stats) {
            s.setStatDate(statDate);
            s.setOrderCount(0);
            s.setOrderAmount(BigDecimal.ZERO);
            s.setCreateTime(new Date());
            dailyMapper.insert(s);
        }

        int count = 0;
        for (DividendDailyStat s : stats) {
            BigDecimal contribution = s.getContributionValue();
            // ratio = 个人贡献 / 当日总贡献；总贡献为0时比例记0
            BigDecimal ratio = contribution.divide(totalContribution, SCALE, RoundingMode.HALF_UP);
            BigDecimal estimated = moneyPool.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

            if (snapshotMapper.selectByDateMemberBatch(statDate, s.getMemberId(), calcBatch) == null) {
                DividendSnapshot snap = new DividendSnapshot();
                snap.setStatDate(statDate);
                snap.setMemberId(s.getMemberId());
                snap.setContributionValue(contribution);
                snap.setPoolTotal(totalContribution);
                snap.setRatio(ratio);
                snap.setEstimatedAmount(estimated);
                snap.setSimulateFlag(1);
                snap.setCalcBatch(calcBatch);
                snap.setCreateTime(new Date());
                snapshotMapper.insert(snap);
                DividendAllocateDetail d = new DividendAllocateDetail();
                d.setStatDate(statDate);
                d.setMemberId(s.getMemberId());
                d.setRatio(ratio);
                d.setEstimatedAmount(estimated);
                d.setSimulateFlag(1);
                d.setCreateTime(new Date());
                allocateMapper.insert(d);
                count++;
            }
        }
        return CommonResult.success(count);
    }

    @Override
    public List<DividendSnapshot> getSnapshot(Date statDate) {
        return snapshotMapper.selectByStatDate(statDate);
    }

    @Override
    public List<DividendSnapshot> getMySnapshot(Date statDate, Long memberId) {
        return snapshotMapper.selectByStatDateAndMember(statDate, memberId);
    }

    @Override
    public List<DividendAllocateDetail> getAllocateDetail(Date statDate) {
        return allocateMapper.selectByStatDate(statDate);
    }
}
