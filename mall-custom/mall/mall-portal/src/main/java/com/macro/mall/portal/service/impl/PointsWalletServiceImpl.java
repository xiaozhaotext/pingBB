package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.UmsPointsAccountMapper;
import com.macro.mall.portal.dao.UmsPointsLedgerMapper;
import com.macro.mall.portal.domain.UmsPointsAccount;
import com.macro.mall.portal.domain.UmsPointsLedger;
import com.macro.mall.portal.service.PointsWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class PointsWalletServiceImpl implements PointsWalletService {

    @Autowired
    private UmsPointsAccountMapper accountMapper;
    @Autowired
    private UmsPointsLedgerMapper ledgerMapper;

    @Override
    public UmsPointsAccount getAccount(Long memberId, int pointsType) {
        return accountMapper.selectByMemberAndType(memberId, pointsType);
    }

    @Override
    public java.util.List<UmsPointsLedger> getLedger(Long memberId, Integer pointsType) {
        return ledgerMapper.selectByMember(memberId, pointsType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> changePoints(Long memberId, int pointsType, String changeType,
                                          long amount, String bizId, String idempotencyKey,
                                          Long operatorId, String remark) {
        // 1) 幂等：同一 idempotency_key 只允许一次
        if (ledgerMapper.selectByIdempotencyKey(idempotencyKey) != null) {
            return CommonResult.failed("重复请求：idempotency_key 已存在，已防止重复发放");
        }
        // 2) 乐观锁重试（最多5次）
        for (int attempt = 0; attempt < 5; attempt++) {
            UmsPointsAccount acc = getOrInit(memberId, pointsType);
            long newBalance = acc.getBalance() + amount;
            if (newBalance < 0) {
                return CommonResult.failed("积分不足，禁止负余额");
            }
            long newAvailable = acc.getAvailable() + amount;
            acc.setBalance(newBalance);
            acc.setAvailable(newAvailable);
            int rows = accountMapper.updateWithVersion(acc); // WHERE id=? AND version=?
            if (rows > 0) {
                // 3) 余额更新成功后写流水（保证账本可追溯）
                UmsPointsLedger ledger = new UmsPointsLedger();
                ledger.setMemberId(memberId);
                ledger.setPointsType(pointsType);
                ledger.setChangeType(changeType);
                ledger.setAmount(amount);
                ledger.setBalanceAfter(newBalance);
                ledger.setBizId(bizId);
                ledger.setIdempotencyKey(idempotencyKey);
                ledger.setOperatorId(operatorId);
                ledger.setRemark(remark);
                ledger.setCreateTime(new Date());
                ledgerMapper.insert(ledger);
                return CommonResult.success(null);
            }
            // version 冲突，重试
        }
        return CommonResult.failed("并发更新冲突，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> freeze(Long memberId, int pointsType, long amount, String idempotencyKey, Long operatorId, String remark) {
        if (ledgerMapper.selectByIdempotencyKey(idempotencyKey) != null) {
            return CommonResult.failed("重复请求：idempotency_key 已存在");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            UmsPointsAccount acc = getOrInit(memberId, pointsType);
            if (acc.getAvailable() < amount) {
                return CommonResult.failed("可用积分不足，无法冻结");
            }
            acc.setAvailable(acc.getAvailable() - amount);
            acc.setFrozen(acc.getFrozen() + amount);
            int rows = accountMapper.updateWithVersion(acc);
            if (rows > 0) {
                UmsPointsLedger ledger = buildLedger(memberId, pointsType, "freeze", -amount, acc.getBalance(), idempotencyKey, operatorId, remark);
                ledgerMapper.insert(ledger);
                return CommonResult.success(null);
            }
        }
        return CommonResult.failed("并发更新冲突，请重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> unfreeze(Long memberId, int pointsType, long amount, String idempotencyKey, Long operatorId, String remark) {
        if (ledgerMapper.selectByIdempotencyKey(idempotencyKey) != null) {
            return CommonResult.failed("重复请求：idempotency_key 已存在");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            UmsPointsAccount acc = getOrInit(memberId, pointsType);
            if (acc.getFrozen() < amount) {
                return CommonResult.failed("冻结积分不足，无法解冻");
            }
            acc.setFrozen(acc.getFrozen() - amount);
            acc.setAvailable(acc.getAvailable() + amount);
            int rows = accountMapper.updateWithVersion(acc);
            if (rows > 0) {
                UmsPointsLedger ledger = buildLedger(memberId, pointsType, "unfreeze", amount, acc.getBalance(), idempotencyKey, operatorId, remark);
                ledgerMapper.insert(ledger);
                return CommonResult.success(null);
            }
        }
        return CommonResult.failed("并发更新冲突，请重试");
    }

    private UmsPointsAccount getOrInit(Long memberId, int pointsType) {
        UmsPointsAccount acc = accountMapper.selectByMemberAndType(memberId, pointsType);
        if (acc == null) {
            acc = new UmsPointsAccount();
            acc.setMemberId(memberId);
            acc.setPointsType(pointsType);
            acc.setBalance(0L);
            acc.setFrozen(0L);
            acc.setAvailable(0L);
            acc.setVersion(0L);
            acc.setCreateTime(new Date());
            accountMapper.insert(acc);
            acc = accountMapper.selectByMemberAndType(memberId, pointsType);
        }
        return acc;
    }

    private UmsPointsLedger buildLedger(Long memberId, int pointsType, String changeType, long amount,
                                       long balanceAfter, String idempotencyKey, Long operatorId, String remark) {
        UmsPointsLedger l = new UmsPointsLedger();
        l.setMemberId(memberId);
        l.setPointsType(pointsType);
        l.setChangeType(changeType);
        l.setAmount(amount);
        l.setBalanceAfter(balanceAfter);
        l.setIdempotencyKey(idempotencyKey);
        l.setOperatorId(operatorId);
        l.setRemark(remark);
        l.setCreateTime(new Date());
        return l;
    }
}
