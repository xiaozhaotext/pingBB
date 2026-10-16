package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.*;

/** Reads the same spendable wallet tables used by consumer checkout. */
@RestController
@RequestMapping("/p0/wallet")
public class WalletAdminController {
    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;
    public WalletAdminController(JdbcTemplate jdbc, P0AdminAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public CommonResult<Map<String,Object>> overview(Principal principal,
            @RequestParam(defaultValue="accounts") String tab,
            @RequestParam(required=false) Long memberId,
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size) {
        access.requireAuthorized(principal);
        if (memberId != null && memberId <= 0) return CommonResult.validateFailed("会员ID必须大于0");
        if (!List.of("accounts","recharges","ledger").contains(tab)) return CommonResult.validateFailed("无效的钱包分类");
        int limit = Math.min(Math.max(size,1),100);
        long offset = (Math.max(page,1)-1L)*limit;
        Object[] filterArgs = memberId == null ? new Object[0] : new Object[]{memberId};
        String filter = memberId == null ? "" : " WHERE member_id=?";
        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("balance",money(jdbc.queryForObject("SELECT COALESCE(SUM(balance_cents),0) FROM ums_recharge_account"+filter,BigDecimal.class,filterArgs)));
        summary.put("recharged",money(jdbc.queryForObject("SELECT COALESCE(SUM(amount_cents),0) FROM ums_recharge_order WHERE status='PAID'"+(memberId==null?"":" AND member_id=?"),BigDecimal.class,filterArgs)));
        summary.put("manualCredited",money(jdbc.queryForObject("SELECT COALESCE(SUM(amount_cents),0) FROM ums_recharge_ledger WHERE change_type='admin_recharge'"+(memberId==null?"":" AND member_id=?"),BigDecimal.class,filterArgs)));
        for (var entry : Map.of("spent","order_pay","refunded","order_refund","withdrawalTransferred","sandbox_withdrawal").entrySet()) {
            var args = new ArrayList<Object>();args.add(entry.getValue());Collections.addAll(args,filterArgs);
            BigDecimal cents = jdbc.queryForObject("SELECT COALESCE(SUM(amount_cents),0) FROM ums_recharge_ledger WHERE change_type=?"+(memberId==null?"":" AND member_id=?"),BigDecimal.class,args.toArray());
            summary.put(entry.getKey(),money("spent".equals(entry.getKey())?cents.negate():cents));
        }
        String from;
        String columns;
        String sort;
        if ("accounts".equals(tab)) {
            from=" FROM ums_recharge_account w LEFT JOIN ums_member m ON m.id=w.member_id";
            columns="w.member_id,m.username,w.balance_cents";
            sort="w.member_id DESC";
        } else if ("recharges".equals(tab)) {
            from=" FROM ums_recharge_order w LEFT JOIN ums_member m ON m.id=w.member_id";
            columns="w.member_id,m.username,w.order_no,w.amount_cents,w.channel,w.status,w.transaction_id,w.balance_after_cents,w.created_at,w.paid_at";
            sort="w.created_at DESC,w.order_no DESC";
        } else {
            from=" FROM ums_recharge_ledger w LEFT JOIN ums_member m ON m.id=w.member_id";
            columns="w.id,w.member_id,m.username,w.change_type,w.amount_cents,w.balance_after_cents,w.biz_id,w.remark,w.create_time";
            sort="w.id DESC";
        }
        String where = memberId==null?"":" WHERE w.member_id=?";
        Long total = jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,filterArgs);
        var args = new ArrayList<Object>();Collections.addAll(args,filterArgs);args.add(limit);args.add(offset);
        var rows = jdbc.queryForList("SELECT "+columns+from+where+" ORDER BY "+sort+" LIMIT ? OFFSET ?",args.toArray());
        for (var row : rows) {
            for (String field : List.of("balance_cents","amount_cents","balance_after_cents")) {
                if (row.containsKey(field)) row.put(field.replace("_cents",""),row.get(field)==null?null:money(new BigDecimal(row.get(field).toString())));
                row.remove(field);
            }
        }
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("summary",summary);data.put("rows",rows);data.put("total",total);
        return CommonResult.success(data);
    }

    /** Platform credit is a wallet liability, not a paid recharge order or cash receipt. */
    @PostMapping("/manual-recharge")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String,Object>> manualRecharge(Principal principal, @RequestBody ManualRecharge request) {
        long adminId = access.requireAuthorized(principal);
        if (request == null || request.memberId() == null || request.memberId() <= 0)
            return CommonResult.validateFailed("会员ID必须大于0");
        if (request.amount() == null || request.amount().scale() > 2 || request.amount().signum() <= 0
                || request.amount().compareTo(new BigDecimal("50000.00")) > 0)
            return CommonResult.validateFailed("单笔加款范围为0.01至50000.00元");
        if (request.requestId() == null || !request.requestId().matches("[0-9a-fA-F-]{36}"))
            return CommonResult.validateFailed("请求编号无效");
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 2 || reason.length() > 150) return CommonResult.validateFailed("请填写2至150字的加款原因");
        long cents = request.amount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        String key = "admin:" + request.requestId();
        String biz = "ADMIN:" + adminId;
        // Lock the existing account to serialize credits and purchases for this member.
        Long exists = jdbc.query("SELECT id FROM ums_member WHERE id=? AND status=1", rs -> rs.next() ? rs.getLong(1) : null, request.memberId());
        if (exists == null) return CommonResult.validateFailed("会员不存在或已停用");
        jdbc.update("INSERT INTO ums_recharge_account(member_id,balance_cents) VALUES(?,0) ON DUPLICATE KEY UPDATE member_id=member_id", request.memberId());
        long balance = jdbc.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=? FOR UPDATE", Long.class, request.memberId());
        var previous = jdbc.queryForList("SELECT member_id,amount_cents,biz_id,remark,balance_after_cents FROM ums_recharge_ledger WHERE idempotency_key=? FOR UPDATE", key);
        if (!previous.isEmpty()) {
            var row = previous.get(0);
            if (((Number)row.get("member_id")).longValue()!=request.memberId() || ((Number)row.get("amount_cents")).longValue()!=cents
                    || !biz.equals(row.get("biz_id")) || !reason.equals(row.get("remark")))
                return CommonResult.validateFailed("同一请求编号不能修改会员、金额或原因");
            return CommonResult.success(Map.of("memberId",request.memberId(),"balance",money(BigDecimal.valueOf(((Number)row.get("balance_after_cents")).longValue())),"replayed",true));
        }
        long next = Math.addExact(balance,cents);
        jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?",next,request.memberId());
        jdbc.update("INSERT INTO ums_recharge_ledger(member_id,change_type,amount_cents,balance_after_cents,biz_id,idempotency_key,remark) VALUES(?,'admin_recharge',?,?,?,?,?)",
                request.memberId(),cents,next,biz,key,reason);
        return CommonResult.success(Map.of("memberId",request.memberId(),"balance",money(BigDecimal.valueOf(next)),"replayed",false));
    }

    public record ManualRecharge(Long memberId, BigDecimal amount, String requestId, String reason) { }

    private static String money(BigDecimal cents) { return cents.movePointLeft(2).setScale(2).toPlainString(); }
}
