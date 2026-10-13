package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only aggregates from the current database. No sample or cached figures. */
@RestController
@Tag(name = "DashboardController", description = "后台真实数据概览")
@RequestMapping("/dashboard")
public class DashboardController {
    /** 用户维度图表窗口天数。 */
    private static final int USER_TREND_DAYS = 14;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public DashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询商品、订单、会员、用户趋势及最近七日已支付订单聚合")
    public CommonResult<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productCount", count("SELECT COUNT(*) FROM pms_product"));
        result.put("productOnSaleCount", count("SELECT COUNT(*) FROM pms_product WHERE delete_status = 0 AND publish_status = 1"));
        result.put("productOffSaleCount", count("SELECT COUNT(*) FROM pms_product WHERE delete_status = 0 AND publish_status = 0"));
        result.put("lowStockSkuCount", count("SELECT COUNT(*) FROM pms_sku_stock WHERE stock <= 10"));
        result.put("orderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0"));
        result.put("pendingDeliveryCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 1"));
        long memberCount = count("SELECT COUNT(*) FROM ums_member");
        result.put("memberCount", memberCount);
        result.put("todayOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY"));
        result.put("todayPaidAmount", amount("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1, 2, 3) AND payment_time >= CURDATE() AND payment_time < CURDATE() + INTERVAL 1 DAY"));
        result.put("totalPaidAmount", amount("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1, 2, 3) AND payment_time IS NOT NULL"));
        // 已成功退款的渠道退款金额；oms_refund_record 未创建时按 0 处理，不阻断概览。
        BigDecimal totalRefunded = sumRefund("SELECT COALESCE(SUM(refund_amount), 0) FROM oms_refund_record WHERE state = 'SUCCESS'");
        BigDecimal todayRefunded = sumRefund("SELECT COALESCE(SUM(refund_amount), 0) FROM oms_refund_record WHERE state = 'SUCCESS' AND create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY");
        result.put("totalRefundedAmount", totalRefunded);
        result.put("todayRefundedAmount", todayRefunded);
        result.put("todayNetPaidAmount", amount("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1, 2, 3) AND payment_time >= CURDATE() AND payment_time < CURDATE() + INTERVAL 1 DAY").subtract(todayRefunded));
        result.put("totalNetPaidAmount", amount("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1, 2, 3) AND payment_time IS NOT NULL").subtract(totalRefunded));
        List<Map<String, Object>> trend = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(payment_time, '%Y-%m-%d') AS day, COUNT(*) AS orderCount, " +
                "COALESCE(SUM(pay_amount), 0) AS paidAmount FROM oms_order " +
                "WHERE delete_status = 0 AND status IN (1, 2, 3) " +
                "AND payment_time >= CURDATE() - INTERVAL 6 DAY " +
                "AND payment_time < CURDATE() + INTERVAL 1 DAY " +
                "GROUP BY DATE_FORMAT(payment_time, '%Y-%m-%d') ORDER BY day");
        result.put("paidTrend", trend);
        // ===== 用户维度：总用户 / 活跃用户 / 新增用户 / 每日趋势 / 区域 / 性别 =====
        result.put("totalUser", memberCount);
        result.put("todayNewMemberCount", count("SELECT COUNT(*) FROM ums_member WHERE create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY"));
        result.put("newUserCount", count("SELECT COUNT(*) FROM ums_member WHERE create_time >= CURDATE() - INTERVAL 29 DAY AND create_time < CURDATE() + INTERVAL 1 DAY"));
        result.put("activeUserCount", count("SELECT COUNT(*) FROM ("
                + "SELECT member_id FROM ums_member_login_log WHERE create_time >= CURDATE() - INTERVAL 29 DAY "
                + "UNION SELECT member_id FROM oms_order WHERE delete_status = 0 AND create_time >= CURDATE() - INTERVAL 29 DAY) t"));
        result.put("userTrend", userTrend());
        result.put("memberRegionStats", jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(city), ''), '未填写') AS name, COUNT(*) AS cnt FROM ums_member GROUP BY name ORDER BY cnt DESC LIMIT 10"));
        result.put("memberGenderStats", jdbcTemplate.queryForList(
                "SELECT CASE gender WHEN 1 THEN '男' WHEN 2 THEN '女' ELSE '未知' END AS name, COUNT(*) AS cnt FROM ums_member GROUP BY name ORDER BY cnt DESC"));
        result.put("userMetricNote", "活跃用户=近30日有登录或有下单的去重会员；新增用户=近30日注册；区域/性别按 ums_member 字段聚合");
        result.put("amountNote", "已支付订单金额已扣除成功退款（oms_refund_record）；当前为本地测试数据库");
        return CommonResult.success(result);
    }

    /** 近 USER_TREND_DAYS 日的总用户（累计）、活跃用户、新增用户。 */
    private List<Map<String, Object>> userTrend() {
        Map<String, long[]> byDay = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS cnt FROM ums_member "
                        + "WHERE create_time >= CURDATE() - INTERVAL " + (USER_TREND_DAYS - 1) + " DAY "
                        + "AND create_time < CURDATE() + INTERVAL 1 DAY GROUP BY day")) {
            byDay.computeIfAbsent(String.valueOf(row.get("day")), key -> new long[2])[0] = ((Number) row.get("cnt")).longValue();
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT day, COUNT(DISTINCT member_id) AS cnt FROM ("
                        + "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, member_id FROM ums_member_login_log "
                        + "WHERE create_time >= CURDATE() - INTERVAL " + (USER_TREND_DAYS - 1) + " DAY "
                        + "UNION ALL SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, member_id FROM oms_order "
                        + "WHERE delete_status = 0 AND create_time >= CURDATE() - INTERVAL " + (USER_TREND_DAYS - 1) + " DAY) t GROUP BY day")) {
            byDay.computeIfAbsent(String.valueOf(row.get("day")), key -> new long[2])[1] = ((Number) row.get("cnt")).longValue();
        }
        long running = count("SELECT COUNT(*) FROM ums_member WHERE create_time < CURDATE() - INTERVAL " + (USER_TREND_DAYS - 1) + " DAY");
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(USER_TREND_DAYS - 1L);
        for (int i = 0; i < USER_TREND_DAYS; i++) {
            String day = start.plusDays(i).format(DAY);
            long[] value = byDay.getOrDefault(day, new long[2]);
            running += value[0];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", day);
            item.put("totalUser", running);
            item.put("activeUser", value[1]);
            item.put("newUser", value[0]);
            trend.add(item);
        }
        return trend;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal amount(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 退款汇总：退款表尚未创建时返回 0，避免概览接口整体报错。 */
    private BigDecimal sumRefund(String sql) {
        try {
            return amount(sql);
        } catch (org.springframework.dao.DataAccessException e) {
            return BigDecimal.ZERO;
        }
    }
}
