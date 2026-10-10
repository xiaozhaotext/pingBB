package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only aggregates from the current database. No sample or cached figures. */
@RestController
@Tag(name = "DashboardController", description = "后台真实数据概览")
@RequestMapping("/dashboard")
public class DashboardController {
    private final JdbcTemplate jdbcTemplate;

    public DashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询商品、订单、会员及最近七日已支付订单聚合")
    public CommonResult<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productCount", count("SELECT COUNT(*) FROM pms_product"));
        result.put("orderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0"));
        result.put("memberCount", count("SELECT COUNT(*) FROM ums_member"));
        result.put("todayOrderCount", count("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY"));
        result.put("todayPaidAmount", amount("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1, 2, 3) AND payment_time >= CURDATE() AND payment_time < CURDATE() + INTERVAL 1 DAY"));
        result.put("totalPaidAmount", amount("SELECT COALESCE(SUM(pay_amount), 0) FROM oms_order WHERE delete_status = 0 AND status IN (1, 2, 3) AND payment_time IS NOT NULL"));
        List<Map<String, Object>> trend = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(payment_time, '%Y-%m-%d') AS day, COUNT(*) AS orderCount, " +
                "COALESCE(SUM(pay_amount), 0) AS paidAmount FROM oms_order " +
                "WHERE delete_status = 0 AND status IN (1, 2, 3) " +
                "AND payment_time >= CURDATE() - INTERVAL 6 DAY " +
                "AND payment_time < CURDATE() + INTERVAL 1 DAY " +
                "GROUP BY DATE_FORMAT(payment_time, '%Y-%m-%d') ORDER BY day");
        result.put("paidTrend", trend);
        result.put("amountNote", "已支付订单金额，未扣退款；当前为本地测试数据库");
        return CommonResult.success(result);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal amount(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}
