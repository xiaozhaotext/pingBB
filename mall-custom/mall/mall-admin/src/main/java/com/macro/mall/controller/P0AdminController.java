package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P0 back-office reads and audited simulation operations. No real payouts. */
@RestController
@RequestMapping("/p0")
public class P0AdminController {
    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;

    public P0AdminController(JdbcTemplate jdbc, P0AdminAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping("/members")
    public CommonResult<List<Map<String, Object>>> members(Principal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        access.requireAuthorized(principal);
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = (Math.max(page, 1) - 1) * limit;
        String like = "%" + keyword.trim() + "%";
        return CommonResult.success(jdbc.queryForList(
                "SELECT m.id,m.username,m.nickname,m.phone,m.status,m.create_time," +
                "COALESCE(i.identity_type,0) AS identity_type,r.inviter_id " +
                "FROM ums_member m LEFT JOIN ums_member_identity i ON i.member_id=m.id " +
                "LEFT JOIN ums_member_recommend r ON r.member_id=m.id " +
                "WHERE m.username LIKE ? OR m.nickname LIKE ? OR m.phone LIKE ? " +
                "ORDER BY m.id DESC LIMIT ? OFFSET ?", like, like, like, limit, offset));
    }

    @GetMapping("/members/{memberId}")
    public CommonResult<Map<String, Object>> member(Principal principal, @PathVariable long memberId) {
        access.requireAuthorized(principal);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,username,nickname,phone,status,create_time,member_level_id FROM ums_member WHERE id=?", memberId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会员不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", rows.get(0));
        data.put("identity", jdbc.queryForList(
                "SELECT identity_type,status,become_time,expire_time,operator_id FROM ums_member_identity WHERE member_id=?", memberId));
        data.put("identityHistory", jdbc.queryForList(
                "SELECT from_type,to_type,reason,operator_id,create_time FROM ums_member_identity_log WHERE member_id=? ORDER BY id DESC LIMIT 50", memberId));
        data.put("inviter", jdbc.queryForList(
                "SELECT r.inviter_id,m.username AS inviter_name,r.bind_time FROM ums_member_recommend r " +
                "JOIN ums_member m ON m.id=r.inviter_id WHERE r.member_id=?", memberId));
        data.put("invitees", jdbc.queryForList(
                "SELECT r.member_id,m.username,r.bind_time FROM ums_member_recommend r " +
                "JOIN ums_member m ON m.id=r.member_id WHERE r.inviter_id=? ORDER BY r.id DESC LIMIT 100", memberId));
        return CommonResult.success(data);
    }

    @PostMapping("/members/{memberId}/identity")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> changeIdentity(Principal principal, @PathVariable long memberId,
            @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        int newType = integer(body, "identityType");
        String reason = requiredText(body, "reason", 5, 200);
        if (newType < 0 || newType > 2) return CommonResult.failed("身份类型无效");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ums_member WHERE id=?", Integer.class, memberId) == 0) {
            return CommonResult.failed("会员不存在");
        }
        List<Integer> current = jdbc.queryForList(
                "SELECT identity_type FROM ums_member_identity WHERE member_id=? FOR UPDATE", Integer.class, memberId);
        int oldType = current.isEmpty() ? 0 : current.get(0);
        if (oldType == newType) return CommonResult.success(null);
        if (current.isEmpty()) {
            jdbc.update("INSERT INTO ums_member_identity(member_id,identity_type,status,become_time,operator_id) " +
                    "VALUES(?,?,1,NOW(),?)", memberId, newType, operatorId);
        } else {
            jdbc.update("UPDATE ums_member_identity SET identity_type=?,status=1,become_time=NOW(),operator_id=? " +
                    "WHERE member_id=?", newType, operatorId, memberId);
        }
        jdbc.update("INSERT INTO ums_member_identity_log(member_id,from_type,to_type,reason,operator_id) " +
                "VALUES(?,?,?,?,?)", memberId, oldType, newType, reason, operatorId);
        audit(operatorId, principal.getName(), "member", "identity_change", memberId,
                oldType + " -> " + newType + ": " + reason);
        return CommonResult.success(null);
    }

    @GetMapping("/partner")
    public CommonResult<Map<String, Object>> partner(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", jdbc.queryForList("SELECT id,price,product_points,repurchase_points,simulate_flag,enabled,remark " +
                "FROM biz_partner_config ORDER BY id DESC LIMIT 10"));
        data.put("orders", jdbc.queryForList("SELECT o.id,o.member_id,m.username,o.amount,o.pay_status,o.simulate_flag," +
                "o.biz_id,o.create_time,o.pay_time FROM biz_partner_order o JOIN ums_member m ON m.id=o.member_id " +
                "ORDER BY o.id DESC LIMIT 100"));
        data.put("identities", jdbc.queryForList("SELECT i.member_id,m.username,i.become_time,i.operator_id " +
                "FROM ums_member_identity i JOIN ums_member m ON m.id=i.member_id " +
                "WHERE i.identity_type=1 AND i.status=1 ORDER BY i.become_time DESC LIMIT 100"));
        return CommonResult.success(data);
    }

    @GetMapping("/cofounder")
    public CommonResult<Map<String, Object>> cofounder(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", jdbc.queryForList("SELECT id,price,cofounder_points,share_reward_cap,dividend_count," +
                "simulate_flag,enabled,remark FROM biz_cofounder_config ORDER BY id DESC LIMIT 10"));
        data.put("orders", jdbc.queryForList("SELECT o.id,o.member_id,m.username,o.amount,o.pay_status,o.simulate_flag," +
                "o.biz_id,o.create_time,o.pay_time FROM biz_cofounder_order o JOIN ums_member m ON m.id=o.member_id " +
                "ORDER BY o.id DESC LIMIT 100"));
        data.put("identities", jdbc.queryForList("SELECT i.member_id,m.username,i.become_time,i.operator_id " +
                "FROM ums_member_identity i JOIN ums_member m ON m.id=i.member_id " +
                "WHERE i.identity_type=2 AND i.status=1 ORDER BY i.become_time DESC LIMIT 100"));
        return CommonResult.success(data);
    }

    @GetMapping("/points")
    public CommonResult<Map<String, Object>> points(Principal principal, @RequestParam(required = false) Long memberId) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        if (memberId == null) {
            data.put("accounts", jdbc.queryForList("SELECT a.member_id,m.username,a.points_type,a.balance,a.frozen," +
                    "a.available FROM ums_points_account a JOIN ums_member m ON m.id=a.member_id " +
                    "ORDER BY a.member_id DESC,a.points_type LIMIT 100"));
            data.put("ledger", jdbc.queryForList("SELECT id,member_id,points_type,change_type,amount,balance_after," +
                    "biz_id,operator_id,remark,create_time FROM ums_points_ledger ORDER BY id DESC LIMIT 100"));
        } else {
            data.put("accounts", jdbc.queryForList("SELECT member_id,points_type,balance,frozen,available " +
                    "FROM ums_points_account WHERE member_id=? ORDER BY points_type", memberId));
            data.put("ledger", jdbc.queryForList("SELECT id,member_id,points_type,change_type,amount,balance_after," +
                    "biz_id,operator_id,remark,create_time FROM ums_points_ledger WHERE member_id=? " +
                    "ORDER BY id DESC LIMIT 100", memberId));
        }
        data.put("adjustments", jdbc.queryForList("SELECT id,member_id,points_type,delta,reason,status," +
                "requested_by,reviewed_by,create_time,review_time FROM p0_points_adjust_request ORDER BY id DESC LIMIT 100"));
        return CommonResult.success(data);
    }

    @PostMapping("/points/adjustments")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> requestPointsAdjustment(Principal principal, @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        long memberId = longValue(body, "memberId");
        int pointsType = integer(body, "pointsType");
        long delta = longValue(body, "delta");
        String reason = requiredText(body, "reason", 5, 200);
        String key = requiredText(body, "idempotencyKey", 16, 64);
        if ((pointsType != 1 && pointsType != 2) || delta == 0) return CommonResult.failed("积分类型或调整额无效");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ums_member WHERE id=?", Integer.class, memberId) == 0) {
            return CommonResult.failed("会员不存在");
        }
        try {
            jdbc.update("INSERT INTO p0_points_adjust_request(member_id,points_type,delta,reason,idempotency_key," +
                    "status,requested_by) VALUES(?,?,?,?,?,0,?)", memberId, pointsType, delta, reason, key, operatorId);
        } catch (DuplicateKeyException e) {
            return CommonResult.failed("重复调整申请");
        }
        audit(operatorId, principal.getName(), "points", "adjust_request", memberId, reason);
        return CommonResult.success(null);
    }

    @PostMapping("/points/adjustments/{requestId}/review")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> reviewPointsAdjustment(Principal principal, @PathVariable long requestId,
            @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        boolean approve = Boolean.parseBoolean(String.valueOf(body.get("approve")));
        List<Map<String, Object>> requests = jdbc.queryForList(
                "SELECT member_id,points_type,delta,status FROM p0_points_adjust_request WHERE id=? FOR UPDATE", requestId);
        if (requests.isEmpty()) return CommonResult.failed("申请不存在");
        Map<String, Object> request = requests.get(0);
        if (((Number) request.get("status")).intValue() != 0) return CommonResult.failed("申请已审核");
        long memberId = ((Number) request.get("member_id")).longValue();
        int pointsType = ((Number) request.get("points_type")).intValue();
        long delta = ((Number) request.get("delta")).longValue();
        if (approve) {
            jdbc.update("INSERT INTO ums_points_account(member_id,points_type,balance,frozen,available,version) " +
                    "VALUES(?,?,0,0,0,0) ON DUPLICATE KEY UPDATE member_id=member_id", memberId, pointsType);
            Map<String, Object> account = jdbc.queryForMap("SELECT balance,frozen FROM ums_points_account " +
                    "WHERE member_id=? AND points_type=? FOR UPDATE", memberId, pointsType);
            long balance = ((Number) account.get("balance")).longValue();
            long frozen = ((Number) account.get("frozen")).longValue();
            long next = Math.addExact(balance, delta);
            if (next < frozen || next < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "调整后余额不足或会出现负数");
            }
            jdbc.update("UPDATE ums_points_account SET balance=?,available=?,version=version+1 " +
                    "WHERE member_id=? AND points_type=?", next, next - frozen, memberId, pointsType);
            jdbc.update("INSERT INTO ums_points_ledger(member_id,points_type,change_type,amount,balance_after," +
                    "biz_id,idempotency_key,operator_id,remark) VALUES(?,?,'adjust',?,?,?,?,?,?)",
                    memberId, pointsType, delta, next, "p0-adjust-" + requestId,
                    "p0-adjust-" + requestId, operatorId, "后台审核调整（模拟积分）");
        }
        jdbc.update("UPDATE p0_points_adjust_request SET status=?,reviewed_by=?,review_time=NOW() WHERE id=?",
                approve ? 1 : 2, operatorId, requestId);
        audit(operatorId, principal.getName(), "points", approve ? "adjust_approve" : "adjust_reject",
                memberId, "request=" + requestId);
        return CommonResult.success(null);
    }

    @GetMapping("/finance")
    public CommonResult<Map<String, Object>> finance(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accounts", jdbc.queryForList("SELECT e.member_id,m.username,e.total_earnings,e.available_earnings," +
                "e.frozen_earnings,e.withdrawn FROM ums_earnings e JOIN ums_member m ON m.id=e.member_id " +
                "ORDER BY e.member_id DESC LIMIT 100"));
        data.put("withdraws", jdbc.queryForList("SELECT w.id,w.member_id,m.username,w.amount,w.fee_rate," +
                "w.fee_amount,w.actual_amount,w.status,w.simulate_flag,w.audit_id,w.audit_remark,w.apply_time " +
                "FROM ums_withdraw_apply w JOIN ums_member m ON m.id=w.member_id ORDER BY w.id DESC LIMIT 100"));
        data.put("ledger", jdbc.queryForList("SELECT id,member_id,change_type,amount,balance_after,biz_id," +
                "remark,create_time FROM ums_earnings_ledger ORDER BY id DESC LIMIT 100"));
        return CommonResult.success(data);
    }

    @PostMapping("/finance/withdraws/{applyId}/review")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> reviewWithdraw(Principal principal, @PathVariable long applyId,
            @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        boolean approve = Boolean.parseBoolean(String.valueOf(body.get("approve")));
        String remark = requiredText(body, "remark", 3, 200);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT member_id,amount,status,simulate_flag FROM ums_withdraw_apply WHERE id=? FOR UPDATE", applyId);
        if (rows.isEmpty()) return CommonResult.failed("申请不存在");
        Map<String, Object> request = rows.get(0);
        if (((Number) request.get("status")).intValue() != 0 ||
                ((Number) request.get("simulate_flag")).intValue() != 1) {
            return CommonResult.failed("仅待审核的模拟申请可处理");
        }
        long memberId = ((Number) request.get("member_id")).longValue();
        if (!approve) {
            BigDecimal amount = (BigDecimal) request.get("amount");
            int changed = jdbc.update("UPDATE ums_earnings SET frozen_earnings=frozen_earnings-?," +
                    "available_earnings=available_earnings+?,version=version+1 " +
                    "WHERE member_id=? AND frozen_earnings>=?", amount, amount, memberId, amount);
            if (changed != 1) return CommonResult.failed("冻结金额不足，拒绝操作已取消");
            BigDecimal available = jdbc.queryForObject(
                    "SELECT available_earnings FROM ums_earnings WHERE member_id=?", BigDecimal.class, memberId);
            jdbc.update("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,biz_id," +
                    "idempotency_key,remark) VALUES(?,'withdraw_reject',?,?,?,?,?)",
                    memberId, amount, available, "withdraw-" + applyId,
                    "p0-withdraw-reject-" + applyId, remark);
        }
        jdbc.update("UPDATE ums_withdraw_apply SET status=?,audit_id=?,audit_remark=?,audit_time=NOW() WHERE id=?",
                approve ? 1 : 4, operatorId, remark, applyId);
        audit(operatorId, principal.getName(), "finance", approve ? "withdraw_approve_simulated" : "withdraw_reject",
                applyId, remark);
        return CommonResult.success(null);
    }

    /** Completes an approved withdrawal in the simulated ledger only. */
    @PostMapping("/finance/withdraws/{applyId}/simulate-complete")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> simulateWithdrawComplete(Principal principal, @PathVariable long applyId) {
        long operatorId = access.requireAuthorized(principal);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT member_id,amount,status,simulate_flag FROM ums_withdraw_apply WHERE id=? FOR UPDATE", applyId);
        if (rows.isEmpty()) return CommonResult.failed("申请不存在");
        Map<String, Object> request = rows.get(0);
        if (((Number) request.get("status")).intValue() != 1 ||
                ((Number) request.get("simulate_flag")).intValue() != 1) {
            return CommonResult.failed("仅审核通过的模拟申请可完成");
        }
        long memberId = ((Number) request.get("member_id")).longValue();
        BigDecimal amount = (BigDecimal) request.get("amount");
        int changed = jdbc.update("UPDATE ums_earnings SET frozen_earnings=frozen_earnings-?," +
                "withdrawn=withdrawn+?,version=version+1 WHERE member_id=? AND frozen_earnings>=?",
                amount, amount, memberId, amount);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "冻结金额不足");
        BigDecimal available = jdbc.queryForObject(
                "SELECT available_earnings FROM ums_earnings WHERE member_id=?", BigDecimal.class, memberId);
        jdbc.update("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,biz_id," +
                "idempotency_key,remark) VALUES(?,'withdraw_done',?,?,?,?,?)",
                memberId, amount.negate(), available, "withdraw-" + applyId,
                "p0-withdraw-done-" + applyId, "仅模拟记账，不触发真实打款");
        jdbc.update("UPDATE ums_withdraw_apply SET status=2 WHERE id=?", applyId);
        audit(operatorId, principal.getName(), "finance", "withdraw_simulated_complete", applyId,
                "仅模拟记账，不触发真实打款");
        return CommonResult.success(null);
    }

    @GetMapping("/dividend")
    public CommonResult<Map<String, Object>> dividend(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contributions", jdbc.queryForList("SELECT id,stat_date,member_id,event_type,biz_id," +
                "contribution_value,verify_status,create_time FROM dividend_contribution_event " +
                "ORDER BY id DESC LIMIT 100"));
        data.put("snapshots", jdbc.queryForList("SELECT id,stat_date,member_id,contribution_value,pool_total," +
                "ratio,estimated_amount,calc_batch,simulate_flag FROM dividend_snapshot ORDER BY id DESC LIMIT 100"));
        data.put("exceptions", jdbc.queryForList("SELECT id,stat_date,member_id,reason,detail,create_time " +
                "FROM dividend_exception_audit ORDER BY id DESC LIMIT 100"));
        data.put("note", "仅展示已记录的模拟结果；贡献事件自动核验及实际分红发放未启用");
        return CommonResult.success(data);
    }

    @GetMapping("/settings")
    public CommonResult<Map<String, Object>> settings(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configs", jdbc.queryForList("SELECT config_key,config_value,config_group,remark,update_time " +
                "FROM sys_config ORDER BY config_group,config_key LIMIT 100"));
        data.put("audit", jdbc.queryForList("SELECT id,operator_id,operator_name,module,action,target_id,detail," +
                "create_time FROM sys_audit_log ORDER BY id DESC LIMIT 100"));
        return CommonResult.success(data);
    }

    @PostMapping("/settings")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> updateSetting(Principal principal, @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        String key = requiredText(body, "key", 5, 64);
        String value = body.get("value") == null ? "" : String.valueOf(body.get("value")).trim();
        if (value.length() > 512) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value 过长");
        validateEditableSetting(key, value);
        List<String> old = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? FOR UPDATE",
                (rs, n) -> rs.getString(1), key);
        jdbc.update("INSERT INTO sys_config(config_key,config_value,config_group,remark) " +
                "VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)",
                key, value.isEmpty() ? null : value,
                key.startsWith("membership.") ? "membership" : "site", "平台管理员配置");
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,detail,"
                        + "`operator`,target_type,old_value,new_value) VALUES(?,?, 'settings','update',?,?,?,?,?)",
                operatorId, principal.getName(), key, principal.getName(), "sys_config:" + key,
                old.isEmpty() ? null : old.get(0), value.isEmpty() ? null : value);
        return CommonResult.success(null);
    }

    private void validateEditableSetting(String key, String value) {
        if ("site.title".equals(key) || "site.notice".equals(key)) {
            if (value.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "设置内容不能为空");
            return;
        }
        boolean priceOrCap = key.equals("membership.partner.price")
                || key.equals("membership.cofounder.price")
                || key.equals("membership.referral.partner.cash_cap")
                || key.equals("membership.referral.cofounder.cash_cap");
        boolean points = key.equals("membership.partner.product_points")
                || key.equals("membership.partner.repurchase_points")
                || key.equals("membership.cofounder.product_points")
                || key.equals("membership.cofounder.repurchase_points")
                || key.equals("membership.referral.partner.repurchase_points")
                || key.equals("membership.referral.cofounder.repurchase_points");
        if (priceOrCap) {
            if (value.isEmpty() && key.equals("membership.referral.partner.cash_cap")) return;
            try {
                BigDecimal amount = new BigDecimal(value);
                if (amount.signum() > 0 && amount.scale() <= 2
                        && amount.compareTo(new BigDecimal("9999999999.99")) <= 0) return;
            } catch (NumberFormatException ignored) { }
        } else if (points) {
            try { if (Long.parseLong(value) >= 0) return; }
            catch (NumberFormatException ignored) { }
        } else if (key.equals("membership.referral.cash_rate")) {
            try {
                BigDecimal rate = new BigDecimal(value);
                if (rate.signum() > 0 && rate.compareTo(BigDecimal.ONE) <= 0 && rate.scale() <= 6) return;
            } catch (NumberFormatException ignored) { }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "此配置项不可修改");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置值无效");
    }

    private void audit(long operatorId, String name, String module, String action, Long targetId, String detail) {
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) " +
                "VALUES(?,?,?,?,?,?)", operatorId, name, module, action, targetId, detail);
    }

    private int integer(Map<String, Object> body, String key) {
        try { return Integer.parseInt(String.valueOf(body.get(key))); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 无效"); }
    }

    private long longValue(Map<String, Object> body, String key) {
        try { return Long.parseLong(String.valueOf(body.get(key))); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 无效"); }
    }

    private String requiredText(Map<String, Object> body, String key, int min, int max) {
        String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
        if (value.length() < min || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 长度无效");
        }
        return value;
    }
}
