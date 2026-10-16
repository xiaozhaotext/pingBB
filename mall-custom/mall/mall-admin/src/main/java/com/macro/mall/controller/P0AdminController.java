package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.P0ReferralAdminService;
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
    private final P0ReferralAdminService referrals;
    @org.springframework.beans.factory.annotation.Autowired
    private com.macro.mall.common.membership.TriPoolService triPools;

    public P0AdminController(JdbcTemplate jdbc, P0AdminAccess access,
                             P0ReferralAdminService referrals) {
        this.jdbc = jdbc;
        this.access = access;
        this.referrals = referrals;
    }

    @GetMapping("/members")
    public CommonResult<List<Map<String, Object>>> members(Principal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        access.requireAuthorized(principal);
        int limit = Math.min(Math.max(size, 1), 100);
        long offset = (Math.max(page, 1) - 1L) * limit;
        String like = "%" + keyword.trim() + "%";
        List<Map<String,Object>> result = jdbc.queryForList(
                "SELECT m.id,m.username,m.nickname,m.phone,m.gender,m.status,m.create_time,contact.real_name,contact.email,contact.wechat_account," +
                "(SELECT MAX(s.login_count) FROM ums_member_statistics_info s WHERE s.member_id=m.id) AS login_count," +
                "COALESCE(i.identity_type,0) AS identity_type,r.inviter_id," +
                "COALESCE((SELECT w.balance_cents / 100.00 FROM ums_recharge_account w WHERE w.member_id=m.id),0) AS member_balance," +
                "COALESCE((SELECT e.available_earnings FROM ums_earnings e WHERE e.member_id=m.id),0) AS available_earnings," +
                "COALESCE((SELECT p.available FROM ums_points_account p WHERE p.member_id=m.id AND p.points_type=1),0) AS product_points," +
                "COALESCE((SELECT p.available FROM ums_points_account p WHERE p.member_id=m.id AND p.points_type=2),0) AS rebuy_points " +
                "FROM ums_member m LEFT JOIN ums_member_contact contact ON contact.member_id=m.id LEFT JOIN ums_member_identity i ON i.member_id=m.id " +
                "LEFT JOIN ums_member_recommend r ON r.member_id=m.id " +
                "WHERE m.username LIKE ? OR m.nickname LIKE ? OR m.phone LIKE ? " +
                "ORDER BY m.id DESC LIMIT ? OFFSET ?", like, like, like, limit, offset);
        if (triPools != null && triPools.enabled()) for (var row : result) {
            var summary = triPools.memberSummary(((Number)row.get("id")).longValue());
            String tier = String.valueOf(summary.get("tier"));
            row.put("membership_tier", tier);
            row.remove("identity_type");
            row.put("membership_name", switch(tier) { case "MEMBER" -> "会员"; case "PARTNER" -> "合伙人"; case "COFOUNDER" -> "联创"; default -> "游客"; });
        }
        return CommonResult.success(result);
    }
    @GetMapping("/members/stats")
    public CommonResult<Map<String, Object>> memberStats(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalMembers", scalarLong("SELECT COUNT(*) FROM ums_member"));
        data.put("todayNewMembers", scalarLong("SELECT COUNT(*) FROM ums_member WHERE create_time >= CURDATE()"));
        data.put("newMembers30d", scalarLong("SELECT COUNT(*) FROM ums_member WHERE create_time >= CURDATE() - INTERVAL 29 DAY"));
        data.put("activeMembers30d", scalarLong("SELECT COUNT(DISTINCT member_id) FROM ums_member_login_log WHERE create_time >= CURDATE() - INTERVAL 29 DAY"));
        data.put("todayActiveMembers", scalarLong("SELECT COUNT(DISTINCT member_id) FROM ums_member_login_log WHERE create_time >= CURDATE()"));
        data.put("todayConsumingMembers", scalarLong("SELECT COUNT(DISTINCT member_id) FROM oms_order WHERE delete_status=0 AND status IN(1,2,3) AND create_time >= CURDATE()"));
        data.put("todayConsumingAmount", scalarDecimal("SELECT COALESCE(SUM(pay_amount),0) FROM oms_order WHERE delete_status=0 AND status IN(1,2,3) AND create_time >= CURDATE()"));
        data.put("totalConsumingMembers", scalarLong("SELECT COUNT(DISTINCT member_id) FROM oms_order WHERE delete_status=0 AND status IN(1,2,3)"));
        data.put("totalConsumingAmount", scalarDecimal("SELECT COALESCE(SUM(pay_amount),0) FROM oms_order WHERE delete_status=0 AND status IN(1,2,3)"));
        data.put("avgOrderAmount", scalarDecimal("SELECT COALESCE(AVG(pay_amount),0) FROM oms_order WHERE delete_status=0 AND status IN(1,2,3)"));
        data.put("trend", jdbc.queryForList(
                "SELECT DATE_FORMAT(d.day,'%Y-%m-%d') AS day,"
                        + " (SELECT COUNT(*) FROM ums_member m WHERE DATE(m.create_time)=d.day) AS newMembers,"
                        + " (SELECT COUNT(DISTINCT l.member_id) FROM ums_member_login_log l WHERE DATE(l.create_time)=d.day) AS activeMembers,"
                        + " (SELECT COALESCE(SUM(o.pay_amount),0) FROM oms_order o WHERE o.delete_status=0 AND o.status IN(1,2,3) AND DATE(o.create_time)=d.day) AS amount"
                        + " FROM (SELECT CURDATE() - INTERVAL n DAY AS day FROM"
                        + " (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4"
                        + "  UNION ALL SELECT 5 UNION ALL SELECT 6) t) d ORDER BY d.day"));
        data.put("identityStats", jdbc.queryForList(
                "SELECT COALESCE(i.identity_type,0) AS identityType,COUNT(*) AS members FROM ums_member m"
                        + " LEFT JOIN ums_member_identity i ON i.member_id=m.id GROUP BY COALESCE(i.identity_type,0) ORDER BY identityType"));
        return CommonResult.success(data);
    }

    @GetMapping("/members/{memberId}/orders")
    public CommonResult<List<Map<String, Object>>> memberOrders(Principal principal, @PathVariable long memberId,
                                                                @RequestParam(defaultValue = "") String zone,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        access.requireAuthorized(principal);
        String zoneCode = zone == null ? "" : zone.trim().toUpperCase();
        if (!zoneCode.isEmpty() && !"PICK".equals(zoneCode) && !"REBUY".equals(zoneCode) && !"CONSUME".equals(zoneCode)) {
            return CommonResult.validateFailed("专区标识仅支持 PICK、REBUY 或 CONSUME");
        }
        int limit = Math.min(Math.max(size, 1), 100);
        long offset = (Math.max(page, 1) - 1L) * limit;
        if (zoneCode.isEmpty()) {
            return CommonResult.success(jdbc.queryForList(
                    "SELECT o.id,o.order_sn,o.status,o.pay_amount,o.create_time,"
                            + "GROUP_CONCAT(DISTINCT z.zone_code) AS product_zones "
                            + "FROM oms_order o LEFT JOIN oms_order_item i ON i.order_id=o.id "
                            + "LEFT JOIN pms_product_zone z ON z.product_id=i.product_id "
                            + "WHERE o.member_id=? AND o.delete_status=0 GROUP BY o.id ORDER BY o.id DESC LIMIT ? OFFSET ?",
                    memberId, limit, offset));
        }
        return CommonResult.success(jdbc.queryForList(
                "SELECT o.id,o.order_sn,o.status,o.pay_amount,o.create_time,"
                        + "GROUP_CONCAT(DISTINCT i.product_name) AS product_names "
                        + "FROM oms_order o JOIN oms_order_item i ON i.order_id=o.id "
                        + "JOIN pms_product_zone z ON z.product_id=i.product_id "
                        + "WHERE o.member_id=? AND o.delete_status=0 AND z.zone_code=? GROUP BY o.id ORDER BY o.id DESC LIMIT ? OFFSET ?",
                memberId, zoneCode, limit, offset));
    }

    @GetMapping("/members/{memberId}")
    public CommonResult<Map<String, Object>> member(Principal principal, @PathVariable long memberId) {
        access.requireAuthorized(principal);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT m.id,m.username,m.nickname,m.phone,m.gender,m.status,m.create_time,m.member_level_id," +
                "(SELECT MAX(s.login_count) FROM ums_member_statistics_info s WHERE s.member_id=m.id) AS login_count " +
                "FROM ums_member m WHERE m.id=?", memberId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会员不存在");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", rows.get(0));
        if (triPools != null && triPools.enabled()) data.put("triMembership", triPools.memberSummary(memberId));
        data.put("addresses", jdbc.queryForList("SELECT id,name,phone_number,default_status,province,city,region,detail_address FROM ums_member_receive_address WHERE member_id=? ORDER BY id DESC", memberId));
        data.put("orders", jdbc.queryForList("SELECT o.id,o.order_sn,o.status,o.pay_amount,o.create_time,GROUP_CONCAT(DISTINCT z.zone_code) AS product_zones FROM oms_order o LEFT JOIN oms_order_item i ON i.order_id=o.id LEFT JOIN pms_product_zone z ON z.product_id=i.product_id WHERE o.member_id=? AND o.delete_status=0 GROUP BY o.id ORDER BY o.id DESC LIMIT 100",memberId));
        data.put("consumption", jdbc.queryForList("SELECT COUNT(*) AS order_count,COALESCE(SUM(CASE WHEN status IN(1,2,3) THEN pay_amount ELSE 0 END),0) AS paid_amount FROM oms_order WHERE member_id=? AND delete_status=0",memberId));
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
        // Preserve the newer client's statistics response name, using the same paid-order calculation.
        data.put("consumptionStats", ((java.util.List<?>)data.get("consumption")).get(0));
        return CommonResult.success(data);
    }

    @PostMapping("/members/{memberId}/status")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> changeStatus(Principal principal, @PathVariable long memberId,
                                         @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        int status = integer(body,"status");
        String reason = requiredText(body,"reason",5,200);
        if (status != 0 && status != 1) return CommonResult.validateFailed("状态必须是0或1");
        int changed = jdbc.update("UPDATE ums_member SET status=? WHERE id=?",status,memberId);
        if (changed == 0) return CommonResult.failed("会员不存在");
        audit(operatorId,principal.getName(),"member","status_change",memberId,status+": "+reason);
        return CommonResult.success(null);
    }

    @PostMapping("/members/{memberId}/referrer")
    public CommonResult<Void> reassignReferrer(Principal principal, @PathVariable long memberId,
                                                @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        long inviterId = longValue(body, "inviterId");
        String reason = requiredText(body, "reason", 5, 200);
        referrals.reassign(memberId, inviterId, operatorId, principal.getName(), reason);
        return CommonResult.success(null);
    }

    @PostMapping("/members/{memberId}/identity")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> changeIdentity(Principal principal, @PathVariable long memberId,
            @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        if (triPools != null && triPools.enabled()) return CommonResult.failed("新身份由身份牌支付和有效直邀自动晋升，不支持旧身份手动设置");
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
        if (triPools != null && triPools.enabled()) return CommonResult.success(Map.of(
                "config", List.of(Map.of("晋升条件","直邀3名付费会员","份数","本人1份＋有效直邀会员数","累计上限","按已确认阶梯，最高24000元")),
                "orders", List.of(), "identities",jdbc.queryForList("SELECT t.member_id,m.username,t.tier,t.direct_paid_count,t.updated_at AS become_time FROM tri_member t JOIN ums_member m ON m.id=t.member_id WHERE t.tier IN ('PARTNER','COFOUNDER') ORDER BY t.updated_at DESC LIMIT 100")));
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
        if (triPools != null && triPools.enabled()) return CommonResult.success(Map.of(
                "config", List.of(Map.of("晋升条件","直邀9名付费会员","联创池份数",1,"联创池独立累计上限",1200,"合伙人池累计上限",24000)),
                "orders", List.of(), "identities",jdbc.queryForList("SELECT t.member_id,m.username,t.tier,t.direct_paid_count,t.updated_at AS become_time FROM tri_member t JOIN ums_member m ON m.id=t.member_id WHERE t.tier='COFOUNDER' ORDER BY t.updated_at DESC LIMIT 100")));
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
        // 佣金汇总：可提现佣金余额 / 冻结中 / 已提现佣金 / 累计佣金
        data.put("summary", jdbc.queryForMap("SELECT COUNT(*) member_count," +
                "COALESCE(SUM(total_earnings),0) total_commission," +
                "COALESCE(SUM(available_earnings),0) available_commission," +
                "COALESCE(SUM(frozen_earnings),0) frozen_commission," +
                "COALESCE(SUM(withdrawn),0) withdrawn_commission FROM ums_earnings"));
        // 提现单统计：待审核/已通过/已完成/已拒绝/累计打款
        data.put("withdrawStats", jdbc.queryForMap("SELECT COUNT(*) total," +
                "COALESCE(SUM(status=0),0) pending," +
                "COALESCE(SUM(status=1),0) approved," +
                "COALESCE(SUM(status IN (2,3)),0) completed," +
                "COALESCE(SUM(status=4),0) rejected," +
                "COALESCE(SUM(CASE WHEN status IN (2,3) THEN actual_amount ELSE 0 END),0) paid_out FROM ums_withdraw_apply"));
        // 佣金来源构成（直推奖励/每日分红/分享奖励/模拟发放等）
        data.put("commissionByType", jdbc.queryForList("SELECT change_type,COUNT(*) entries," +
                "COALESCE(SUM(amount),0) amount FROM ums_earnings_ledger GROUP BY change_type ORDER BY amount DESC"));
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
        try {
            data.put("poolStats", jdbc.queryForMap("SELECT pool_balance,total_in,total_out,update_time FROM dividend_pool WHERE id=1"));
        } catch (Exception ignored) {
            data.put("poolStats", null);
        }
        try {
            data.put("poolTodayIn", jdbc.queryForMap("SELECT COALESCE(SUM(change_amount),0) AS today_in FROM dividend_pool_ledger " +
                    "WHERE change_amount>0 AND DATE(create_time)=CURDATE()"));
        } catch (Exception ignored) {
            data.put("poolTodayIn", null);
        }
        try {
            data.put("dailyDetails", jdbc.queryForList("SELECT stat_date, SUM(shares) AS total_shares, " +
                    "COUNT(DISTINCT member_id) AS recipients, SUM(dividend_amount) AS total_amount, " +
                    "ROUND(SUM(dividend_amount)/NULLIF(SUM(shares),0),2) AS per_share_amount, MAX(calc_batch) AS calc_batch " +
                    "FROM member_dividend_ledger GROUP BY stat_date ORDER BY stat_date DESC LIMIT 60"));
        } catch (Exception ignored) {
            data.put("dailyDetails", java.util.Collections.emptyList());
        }
        return CommonResult.success(data);
    }

    @GetMapping("/notices")
    public CommonResult<List<Map<String, Object>>> notices(Principal principal) {
        access.requireAuthorized(principal);
        return CommonResult.success(jdbc.queryForList(
                "SELECT id,title,content,type,status,publish_time,create_time FROM sys_notice ORDER BY id DESC LIMIT 100"));
    }

    @PostMapping("/notices")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> publishNotice(Principal principal, @RequestBody Map<String, Object> body) {        long operatorId = access.requireAuthorized(principal);
        String title = requiredText(body, "title", 1, 128);
        String content = requiredText(body, "content", 1, 10000);
        String type = body.get("type") == null ? "SYSTEM" : String.valueOf(body.get("type"));
        jdbc.update("INSERT INTO sys_notice(title,content,type,status,publish_time)"
                        + " VALUES(?,?,?,1,CURRENT_TIMESTAMP)",
                title, content, type);
        audit(operatorId, principal.getName(), "notice", "publish", null, "发布通知: " + title);
        jdbc.update("INSERT INTO sys_message_record(channel,target,content,biz_type,biz_id,status,error_msg)"
                        + " VALUES(?,?,?,?,?,?,?)",
                "站内信", "全体会员", title, "NOTICE", null, "SENT", null);
        return CommonResult.success(null);
    }

    @GetMapping("/consultations")
    public CommonResult<List<Map<String, Object>>> consultations(Principal principal) {
        access.requireAuthorized(principal);
        return CommonResult.success(jdbc.queryForList(
                "SELECT c.id,c.subject,c.content,c.contact,c.status,c.reply,c.reply_time,c.create_time,"
                        + " m.username AS member_name FROM sys_consultation c"
                        + " LEFT JOIN ums_member m ON m.id=c.member_id ORDER BY c.id DESC LIMIT 100"));
    }

    @PostMapping("/consultations/{id}/reply")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> replyConsultation(Principal principal,
                                                @PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        String reply = requiredText(body, "reply", 1, 5000);
        int updated = jdbc.update("UPDATE sys_consultation SET reply=?,reply_time=CURRENT_TIMESTAMP,status=1"
                        + " WHERE id=? AND status=0",
                reply, id);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "咨询不存在或已回复");
        audit(operatorId, principal.getName(), "consultation", "reply", id, "回复咨询 #" + id);
        return CommonResult.success(null);
    }

    @GetMapping("/settings")
    public CommonResult<Map<String, Object>> settings(Principal principal) {
        access.requireAuthorized(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> configs = jdbc.queryForList("SELECT config_key,config_value,config_group,remark,update_time " +
                "FROM sys_config ORDER BY config_group,config_key LIMIT 100");
        for (Map<String, Object> row : configs) {
            String key = String.valueOf(row.get("config_key"));
            if (isSensitiveKey(key) && row.get("config_value") != null) {
                row.put("config_value", maskSecret(String.valueOf(row.get("config_value"))));
            }
        }
        data.put("configs", configs);
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
        // Sensitive keys: submitting the masked display value (containing ****) means "keep existing".
        if (isSensitiveKey(key) && value.contains("****")) {
            return CommonResult.success(null);
        }
        int maxLen = maxValueLength(key);
        if (value.length() > maxLen) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value 过长");
        validateEditableSetting(key, value);
        List<String> old = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? FOR UPDATE",
                (rs, n) -> rs.getString(1), key);
        jdbc.update("INSERT INTO sys_config(config_key,config_value,config_group,remark) " +
                "VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)",
                key, value.isEmpty() ? null : value,
                configGroup(key), "平台管理员配置");
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,detail,"
                        + "`operator`,target_type,old_value,new_value) VALUES(?,?, 'settings','update',?,?,?,?,?)",
                operatorId, principal.getName(), key, principal.getName(), "sys_config:" + key,
                maskIfSensitive(key, old.isEmpty() ? null : old.get(0)),
                maskIfSensitive(key, value.isEmpty() ? null : value));
        return CommonResult.success(null);
    }

    private void validateEditableSetting(String key, String value) {
        if ("site.title".equals(key) || "site.notice".equals(key)) {
            if (value.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "设置内容不能为空");
            return;
        }
        if (key.equals("membership.partner.product_id") || key.equals("membership.cofounder.product_id")) {
            if (value.isEmpty()) return;
            if (!value.matches("[1-9][0-9]{0,17}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请填写有效旅游商品ID，或留空取消关联");
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pms_product p JOIN pms_product_zone z ON z.product_id=p.id WHERE p.id=? AND p.delete_status=0 AND p.publish_status=1 AND z.zone_code='PICK'",Integer.class,Long.valueOf(value));
            if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请先选择已上架的自选区旅游商品");
            Integer other = jdbc.queryForObject("SELECT COUNT(*) FROM sys_config WHERE config_key IN ('membership.partner.product_id','membership.cofounder.product_id') AND config_key<>? AND config_value=?",Integer.class,key,value);
            if (other != null && other > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"同一旅游商品不可关联两个权益方案");
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
        } else if (key.equals("pay.wechat.mch_id") || key.equals("pay.wechat.app_id")
                || key.equals("pay.alipay.app_id")) {
            if (!value.isEmpty() && value.length() <= 64) return;
        } else if (key.equals("pay.wechat.api_key")) {
            if (!value.isEmpty() && value.length() <= 128) return;
        } else if (key.equals("pay.alipay.private_key") || key.equals("pay.alipay.public_key")) {
            if (!value.isEmpty() && value.length() <= 4096) return;
        } else if (key.equals("sms.provider")) {
            if (value.isEmpty() || value.equals("aliyun") || value.equals("tencent")) return;
        } else if (key.equals("sms.access_key") || key.equals("sms.access_secret")) {
            if (value.length() <= 128) return;
        } else if (key.equals("sms.sign_name")) {
            if (value.length() <= 32) return;
        } else if (key.equals("sms.template_code")) {
            if (value.length() <= 64) return;
        } else if (key.equals("dividend.pool.ratio")) {
            try {
                BigDecimal ratio = new BigDecimal(value);
                if (ratio.signum() >= 0 && ratio.compareTo(BigDecimal.ONE) <= 0 && ratio.scale() <= 4) return;
            } catch (NumberFormatException ignored) { }
        } else if (key.equals("dividend.daily.base")) {
            try {
                BigDecimal base = new BigDecimal(value);
                if (base.signum() >= 0) return;
            } catch (NumberFormatException ignored) { }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "此配置项不可修改");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置值无效");
    }

    private boolean isSensitiveKey(String key) {
        return "pay.wechat.api_key".equals(key)
                || "pay.alipay.private_key".equals(key)
                || "pay.alipay.public_key".equals(key)
                || "sms.access_secret".equals(key);
    }

    private String maskSecret(String value) {
        if (value == null || value.isEmpty()) return value;
        int len = value.length();
        if (len <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(len - 4);
    }

    private String maskIfSensitive(String key, String value) {
        return isSensitiveKey(key) ? maskSecret(value) : value;
    }

    private int maxValueLength(String key) {
        if ("pay.alipay.private_key".equals(key) || "pay.alipay.public_key".equals(key)) return 4096;
        return 512;
    }

    private String configGroup(String key) {
        if (key.startsWith("membership.")) return "membership";
        if (key.startsWith("pay.")) return "pay";
        if (key.startsWith("sms.")) return "sms";
        if (key.startsWith("dividend.")) return "dividend";
        return "site";
    }

    // ===== 登录日志（ums_admin_login_log）=====

    @GetMapping("/login-logs")
    public CommonResult<Map<String, Object>> loginLogs(Principal principal,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        access.requireAuthorized(principal);
        int limit = Math.min(Math.max(size, 1), 100);
        long offset = (Math.max(page, 1) - 1L) * limit;
        String like = "%" + keyword.trim() + "%";
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT l.id,l.admin_id,COALESCE(a.username,'') AS username,l.ip,l.address,l.user_agent,l.create_time "
                        + "FROM ums_admin_login_log l LEFT JOIN ums_admin a ON a.id=l.admin_id "
                        + "WHERE COALESCE(a.username,'') LIKE ? OR l.ip LIKE ? ORDER BY l.id DESC LIMIT ? OFFSET ?",
                like, like, limit, offset);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", jdbc.queryForObject(
                "SELECT COUNT(*) FROM ums_admin_login_log l LEFT JOIN ums_admin a ON a.id=l.admin_id "
                        + "WHERE COALESCE(a.username,'') LIKE ? OR l.ip LIKE ?", Integer.class, like, like));
        data.put("page", Math.max(page, 1));
        data.put("size", limit);
        return CommonResult.success(data);
    }

    // ===== 部门管理（ums_department）=====

    @GetMapping("/departments")
    public CommonResult<List<Map<String, Object>>> departments(Principal principal) {
        access.requireAuthorized(principal);
        return CommonResult.success(jdbc.queryForList(
                "SELECT d.id,d.parent_id,d.name,d.leader,d.phone,d.role_id,d.sort,d.status,d.remark,d.create_time,"
                        + "(SELECT COUNT(*) FROM ums_admin a WHERE a.dept_id=d.id) AS admin_count "
                        + "FROM ums_department d ORDER BY d.parent_id,d.sort,d.id"));
    }

    @PostMapping("/departments")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, Object>> createDepartment(Principal principal, @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        String name = requiredText(body, "name", 1, 64);
        long parentId = body.get("parentId") == null ? 0L : longValue(body, "parentId");
        jdbc.update("INSERT INTO ums_department(parent_id,name,leader,phone,role_id,sort,status,remark,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,NOW())",
                parentId, name, optional(body, "leader", 64), optional(body, "phone", 32),
                body.get("roleId") == null ? null : longValue(body, "roleId"),
                body.get("sort") == null ? 0 : integer(body, "sort"),
                body.get("status") == null ? 1 : integer(body, "status"),
                optional(body, "remark", 255));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit(operatorId, principal.getName(), "department", "create", id, name);
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", id);
        return CommonResult.success(created);
    }

    @PostMapping("/departments/{id}")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> updateDepartment(Principal principal, @PathVariable long id, @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        String name = requiredText(body, "name", 1, 64);
        long parentId = body.get("parentId") == null ? 0L : longValue(body, "parentId");
        if (parentId == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级部门不能是自己");
        }
        jdbc.update("UPDATE ums_department SET parent_id=?,name=?,leader=?,phone=?,role_id=?,sort=?,status=?,remark=? WHERE id=?",
                parentId, name, optional(body, "leader", 64), optional(body, "phone", 32),
                body.get("roleId") == null ? null : longValue(body, "roleId"),
                body.get("sort") == null ? 0 : integer(body, "sort"),
                body.get("status") == null ? 1 : integer(body, "status"),
                optional(body, "remark", 255), id);
        audit(operatorId, principal.getName(), "department", "update", id, name);
        return CommonResult.success(null);
    }

    @PostMapping("/departments/{id}/delete")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> deleteDepartment(Principal principal, @PathVariable long id) {
        long operatorId = access.requireAuthorized(principal);
        Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM ums_department WHERE parent_id=?", Integer.class, id);
        if (children != null && children > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先删除下级部门");
        }
        jdbc.update("UPDATE ums_admin SET dept_id=NULL WHERE dept_id=?", id);
        jdbc.update("DELETE FROM ums_department WHERE id=?", id);
        audit(operatorId, principal.getName(), "department", "delete", id, null);
        return CommonResult.success(null);
    }

    @GetMapping("/departments/{id}/admins")
    public CommonResult<List<Map<String, Object>>> departmentAdmins(Principal principal, @PathVariable long id) {
        access.requireAuthorized(principal);
        return CommonResult.success(jdbc.queryForList(
                "SELECT id,username,nick_name,email,status FROM ums_admin WHERE dept_id=? ORDER BY id", id));
    }

    @PostMapping("/departments/{id}/admins")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> assignDepartmentAdmins(Principal principal, @PathVariable long id,
                                                    @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        List<Long> adminIds = new java.util.ArrayList<>();
        Object raw = body.get("adminIds");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                adminIds.add(Long.parseLong(String.valueOf(item)));
            }
        }
        jdbc.update("UPDATE ums_admin SET dept_id=NULL WHERE dept_id=?", id);
        for (Long adminId : adminIds) {
            jdbc.update("UPDATE ums_admin SET dept_id=? WHERE id=?", id, adminId);
        }
        audit(operatorId, principal.getName(), "department", "assign-admins", id, adminIds.toString());
        return CommonResult.success(null);
    }

    private String optional(Map<String, Object> body, String key, int max) {
        String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
        if (value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 长度无效");
        }
        return value;
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
    private long scalarLong(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal scalarDecimal(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}
