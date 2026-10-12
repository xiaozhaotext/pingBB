package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.UmsEarningsLedgerMapper;
import com.macro.mall.portal.dao.UmsEarningsMapper;
import com.macro.mall.portal.dao.UmsWithdrawApplyMapper;
import com.macro.mall.portal.domain.UmsEarnings;
import com.macro.mall.portal.domain.UmsEarningsLedger;
import com.macro.mall.portal.domain.UmsWithdrawApply;
import com.macro.mall.portal.service.EarningsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * 收益与提现：事务一致 + 幂等 + 乐观锁 + 防负余额。
 * 提现手续费率从 sys_config(withdraw.fee_rate) 读取，默认 0.6%。
 * 真实资金打款需在商户支付配置完成后接入（见 WithdrawController）。
 */
@Service
public class EarningsServiceImpl implements EarningsService {

    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.006");

    @Autowired
    private JdbcTemplate jdbc;

    /** 提现手续费率：优先读 sys_config(withdraw.fee_rate)，无配置或非法时用默认 0.6%。 */
    private BigDecimal feeRate() {
        try {
            List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key='withdraw.fee_rate' LIMIT 1",
                    (rs, n) -> rs.getString(1));
            if (!rows.isEmpty() && rows.get(0) != null) {
                BigDecimal rate = new BigDecimal(rows.get(0).trim());
                if (rate.signum() >= 0 && rate.compareTo(BigDecimal.ONE) <= 0) return rate;
            }
        } catch (Exception ignored) { }
        return DEFAULT_FEE_RATE;
    }

    @Autowired
    private UmsEarningsMapper earningsMapper;
    @Autowired
    private UmsEarningsLedgerMapper ledgerMapper;
    @Autowired
    private UmsWithdrawApplyMapper withdrawMapper;

    @Override
    public UmsEarnings getAccount(Long memberId) {
        return getOrInit(memberId);
    }

    @Override
    public List<UmsEarningsLedger> getLedger(Long memberId) {
        return ledgerMapper.selectByMember(memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> simulateReward(Long memberId, BigDecimal amount, String bizId,
                                             String idempotencyKey, String remark) {
        if (ledgerMapper.selectByIdempotencyKey(idempotencyKey) != null) {
            return CommonResult.failed("重复发放：idempotency_key 已存在");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            UmsEarnings e = getOrInit(memberId);
            BigDecimal newTotal = e.getTotalEarnings().add(amount);
            BigDecimal newAvailable = e.getAvailableEarnings().add(amount);
            e.setTotalEarnings(newTotal);
            e.setAvailableEarnings(newAvailable);
            int rows = earningsMapper.updateWithVersion(e);
            if (rows > 0) {
                UmsEarningsLedger l = new UmsEarningsLedger();
                l.setMemberId(memberId);
                l.setChangeType("simulate_reward");
                l.setAmount(amount);
                l.setBalanceAfter(newTotal);
                l.setBizId(bizId);
                l.setIdempotencyKey(idempotencyKey);
                l.setRemark(remark);
                l.setCreateTime(new Date());
                ledgerMapper.insert(l);
                return CommonResult.success(null);
            }
        }
        return CommonResult.failed("并发更新冲突，请重试");
    }

    @Override
    public List<UmsWithdrawApply> getWithdraws(Long memberId) {
        return withdrawMapper.selectByMember(memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<UmsWithdrawApply> applyWithdraw(Long memberId, BigDecimal amount, String idempotencyKey) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CommonResult.failed("提现金额必须大于0");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            UmsEarnings e = getOrInit(memberId);
            if (e.getAvailableEarnings().compareTo(amount) < 0) {
                return CommonResult.failed("可提现余额不足");
            }
            e.setAvailableEarnings(e.getAvailableEarnings().subtract(amount));
            e.setFrozenEarnings(e.getFrozenEarnings().add(amount));
            int rows = earningsMapper.updateWithVersion(e);
            if (rows > 0) {
                BigDecimal rate = feeRate();
                BigDecimal fee = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal actual = amount.subtract(fee).setScale(2, RoundingMode.HALF_UP);
                UmsWithdrawApply a = new UmsWithdrawApply();
                a.setMemberId(memberId);
                a.setAmount(amount);
                a.setFeeRate(rate);
                a.setFeeAmount(fee);
                a.setActualAmount(actual);
                a.setStatus(0);
                a.setSimulateFlag(0);
                withdrawMapper.insert(a);
                return CommonResult.success(withdrawMapper.selectById(a.getId()));
            }
        }
        return CommonResult.failed("并发更新冲突，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> processWithdraw(Long applyId, Long operatorId, boolean approve, String remark) {
        UmsWithdrawApply a = withdrawMapper.selectById(applyId);
        if (a == null) {
            return CommonResult.failed("提现申请不存在");
        }
        if (a.getStatus() != 0) {
            return CommonResult.failed("该申请已处理，请勿重复操作");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            UmsEarnings e = getOrInit(a.getMemberId());
            if (approve) {
                e.setFrozenEarnings(e.getFrozenEarnings().subtract(a.getAmount()));
                e.setWithdrawn(e.getWithdrawn().add(a.getAmount()));
            } else {
                e.setFrozenEarnings(e.getFrozenEarnings().subtract(a.getAmount()));
                e.setAvailableEarnings(e.getAvailableEarnings().add(a.getAmount()));
            }
            int rows = earningsMapper.updateWithVersion(e);
            if (rows > 0) {
                a.setStatus(approve ? 3 : 4);
                a.setAuditId(operatorId);
                a.setAuditRemark(remark);
                a.setAuditTime(new Date());
                withdrawMapper.updateStatus(a);
                return CommonResult.success(null);
            }
        }
        return CommonResult.failed("并发更新冲突，请重试");
    }

    private UmsEarnings getOrInit(Long memberId) {
        UmsEarnings e = earningsMapper.selectByMember(memberId);
        if (e == null) {
            e = new UmsEarnings();
            e.setMemberId(memberId);
            e.setTotalEarnings(BigDecimal.ZERO);
            e.setAvailableEarnings(BigDecimal.ZERO);
            e.setFrozenEarnings(BigDecimal.ZERO);
            e.setWithdrawn(BigDecimal.ZERO);
            e.setVersion(0L);
            earningsMapper.insert(e);
            e = earningsMapper.selectByMember(memberId);
        }
        return e;
    }
}
