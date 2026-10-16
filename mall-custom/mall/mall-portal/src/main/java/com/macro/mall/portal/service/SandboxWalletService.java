package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsWithdrawApply;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Demo-only earnings withdrawal into the existing spendable wallet. No external payout. */
@Service
public class SandboxWalletService {
    private final JdbcTemplate jdbc;
    private final EarningsService earnings;
    private final RechargeService recharge;
    public SandboxWalletService(JdbcTemplate jdbc,EarningsService earnings,RechargeService recharge) {
        this.jdbc=jdbc;this.earnings=earnings;this.recharge=recharge;
    }
    public boolean enabled() { return recharge.sandboxEnabled(); }
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<UmsWithdrawApply> withdraw(long member,BigDecimal amount,String key) {
        if (!enabled()) return CommonResult.forbidden(null);
        // applyWithdraw locks the member and reuses the original request before any second credit.
        var result=earnings.applyWithdraw(member,amount,key);
        if (result.getCode()!=200) return result;
        var a=result.getData();
        var row=jdbc.queryForMap("SELECT status FROM ums_withdraw_apply WHERE id=? AND member_id=? FOR UPDATE",a.getId(),member);
        int status=((Number)row.get("status")).intValue();
        if (status==3) {
            // Only replay our own completed transfer, not a previously external/manual withdrawal.
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM ums_recharge_ledger WHERE member_id=? AND idempotency_key=?",Integer.class,member,"sandbox:WITHDRAW:"+a.getId());
            if (count==null || count!=1) throw new IllegalArgumentException("该提现已按其他方式处理，不能重复转入余额");
            return result;
        }
        if (status!=0) throw new IllegalArgumentException("该提现申请不能转入余额");
        if (jdbc.update("UPDATE ums_earnings SET frozen_earnings=frozen_earnings-?,withdrawn=withdrawn+?,version=version+1 WHERE member_id=? AND frozen_earnings>=?",a.getAmount(),a.getAmount(),member,a.getAmount())!=1)
            throw new IllegalStateException("冻结收益不足，提现已回滚");
        recharge.creditSandboxWithdrawal(member,a.getActualAmount().movePointRight(2).longValueExact(),a.getId());
        jdbc.update("UPDATE ums_withdraw_apply SET status=3,audit_remark='沙盒提现已转入消费余额，无真实打款',audit_time=CURRENT_TIMESTAMP WHERE id=?",a.getId());
        jdbc.update("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,biz_id,idempotency_key,remark) SELECT member_id,'withdraw_done',?,available_earnings,?,?,? FROM ums_earnings WHERE member_id=?",
                a.getAmount().negate(),"WITHDRAW:"+a.getId(),"sandbox-withdraw:"+a.getId(),"沙盒提现：实到消费余额 "+a.getActualAmount()+" 元，手续费 "+a.getFeeAmount()+" 元",member);
        a.setStatus(3);
        return CommonResult.success(a);
    }
}
