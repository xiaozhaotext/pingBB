package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/p0/wallet-withdrawals")
public class WalletWithdrawalAdminController {
    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;
    public WalletWithdrawalAdminController(JdbcTemplate jdbc,P0AdminAccess access) {this.jdbc=jdbc;this.access=access;}
    public record Decision(boolean paid,String note,String payoutReference) {}

    @GetMapping
    public CommonResult<?> list(Principal principal,@RequestParam(defaultValue="PENDING") String status) {
        access.requireAuthorized(principal);
        if (!List.of("PENDING","PAID","REJECTED").contains(status)) return CommonResult.validateFailed("状态无效");
        return CommonResult.success(jdbc.queryForList("SELECT w.id,w.member_id,m.username,w.gross_cents,w.fee_cents,w.net_cents,w.fee_rate,w.recipient_name,w.channel,w.bank_name,w.bank_branch,w.recipient_account,w.status,w.decision_note,w.payout_reference,w.created_at,w.decided_at FROM ums_wallet_withdrawal w LEFT JOIN ums_member m ON m.id=w.member_id WHERE w.status=? ORDER BY w.id DESC LIMIT 200",status));
    }

    @PostMapping("/{id}/decision")
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<?> decide(Principal principal,@PathVariable long id,@RequestBody Decision request) {
        long operator = access.requireAuthorized(principal);
        if (request==null || request.note()==null || request.note().trim().length()<2 || request.note().length()>255)
            return CommonResult.validateFailed("请填写2至255字的处理说明");
        if (request.paid() && (request.payoutReference()==null || request.payoutReference().isBlank() || request.payoutReference().length()>120))
            return CommonResult.validateFailed("确认已手动打款时必须填写转账凭证号");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM ums_wallet_withdrawal WHERE id=? FOR UPDATE",id);
        if (rows.isEmpty()) return CommonResult.validateFailed("提现申请不存在");
        Map<String,Object> row=rows.get(0);
        if (!"PENDING".equals(row.get("status"))) return CommonResult.validateFailed("申请已处理，请勿重复操作");
        long member=((Number)row.get("member_id")).longValue();
        long cents=((Number)row.get("gross_cents")).longValue();
        if (!request.paid()) {
            long balance=jdbc.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=? FOR UPDATE",Long.class,member);
            long next=Math.addExact(balance,cents);
            jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?",next,member);
            jdbc.update("INSERT INTO ums_recharge_ledger(member_id,change_type,amount_cents,balance_after_cents,biz_id,idempotency_key,remark) VALUES(?,'wallet_withdraw_reject',?,?,?,?,?)",
                    member,cents,next,"WITHDRAW:"+id,"withdraw-reject:"+id,"余额提现申请退回");
        }
        jdbc.update("UPDATE ums_wallet_withdrawal SET status=?,decision_note=?,payout_reference=?,operator_id=?,decided_at=CURRENT_TIMESTAMP WHERE id=?",
                request.paid()?"PAID":"REJECTED",request.note().trim(),request.paid()?request.payoutReference().trim():null,operator,id);
        return CommonResult.success(Map.of("id",id,"status",request.paid()?"PAID":"REJECTED"));
    }
}
