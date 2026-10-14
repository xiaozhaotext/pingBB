package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.DividendAllocateDetail;
import com.macro.mall.portal.domain.DividendSnapshot;
import com.macro.mall.portal.service.DividendDistributionService;
import com.macro.mall.portal.service.DividendPoolService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 每日分红池（多劳多得，模拟）- 消费者端查询 + 运营端记录/分配
 * 规则：ratio = 个人贡献 / 当日总贡献；严禁平均/写死/随机。
 * 不启用真实分红入账与打款。
 */
@RestController
@Tag(name = "DividendPoolController", description = "每日分红池(多劳多得,模拟)")
@RequestMapping("/member/dividend")
public class DividendPoolController {

    @Autowired
    private DividendPoolService dividendPoolService;
    @Autowired
    private DividendDistributionService dividendDistributionService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private JdbcTemplate jdbc;

    @Operation(summary = "执行每日分红分配（真实入账，幂等）")
    @PostMapping("/distribute")
    public CommonResult<Integer> distribute(@RequestBody Map<String, Object> body) {
        return CommonResult.forbidden(null);
    }

    @Operation(summary = "我的分红信息（份数/今日/累计/封顶）")
    @GetMapping("/info")
    public CommonResult<Map<String, Object>> info() {
        Long memberId = memberService.getCurrentMember().getId();
        Map<String, Object> result = new java.util.HashMap<>();

        // 身份与份数：1=合伙人(1份)，2=联创(3份)
        List<Map<String, Object>> idRows = jdbc.queryForList(
                "SELECT identity_type FROM ums_member_identity WHERE member_id=? AND status=1 LIMIT 1",
                memberId);
        int identityType = 0;
        int shares = 0;
        if (!idRows.isEmpty() && idRows.get(0).get("identity_type") != null) {
            identityType = ((Number) idRows.get(0).get("identity_type")).intValue();
            shares = (identityType == 2 ? 3 : (identityType == 1 ? 1 : 0));
        }
        result.put("identityType", identityType);
        result.put("identityName", identityType == 2 ? "联创" : (identityType == 1 ? "合伙人" : "普通会员"));
        result.put("shares", shares);

        // 今日分红
        BigDecimal todayAmount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(dividend_amount),0) FROM member_dividend_ledger WHERE member_id=? AND stat_date=CURDATE()",
                BigDecimal.class, memberId);
        result.put("todayDividend", todayAmount == null ? BigDecimal.ZERO : todayAmount);

        // 累计分红
        BigDecimal totalAmount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(dividend_amount),0) FROM member_dividend_ledger WHERE member_id=?",
                BigDecimal.class, memberId);
        result.put("totalDividend", totalAmount == null ? BigDecimal.ZERO : totalAmount);

        // 推荐人数与封顶：1200 + 1200×推荐人数，上限12000
        Integer refCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ums_member_recommend WHERE inviter_id=?",
                Integer.class, memberId);
        int refs = refCount == null ? 0 : refCount;
        result.put("referralCount", refs);
        BigDecimal capBase = configDecimal("dividend.cap.base", new BigDecimal("1200.00"));
        BigDecimal capPerReferral = configDecimal("dividend.cap.per_referral", new BigDecimal("1200.00"));
        BigDecimal capMax = configDecimal("dividend.cap.max", new BigDecimal("12000.00"));
        BigDecimal cap = capBase.add(capPerReferral.multiply(new BigDecimal(refs)));
        if (cap.compareTo(capMax) > 0) cap = capMax;
        result.put("capAmount", cap);
        result.put("capBase", capBase);
        result.put("capPerReferral", capPerReferral);
        result.put("capMax", capMax);

        // 分红发放开关状态（仅展示，不在此修改）
        result.put("distributionEnabled", configBool("dividend.distribution.enabled", false));

        // 分红池余额（公开信息）
        try {
            BigDecimal poolBalance = jdbc.queryForObject(
                    "SELECT pool_balance FROM dividend_pool WHERE id=1", BigDecimal.class);
            result.put("poolBalance", poolBalance == null ? BigDecimal.ZERO : poolBalance);
        } catch (Exception e) {
            result.put("poolBalance", BigDecimal.ZERO);
        }
        return CommonResult.success(result);
    }

    @Operation(summary = "我的分红记录")
    @GetMapping("/records")
    public CommonResult<List<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long memberId = memberService.getCurrentMember().getId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT stat_date, shares, dividend_amount, cap_amount, cumulative_received, create_time"
                        + " FROM member_dividend_ledger WHERE member_id=? ORDER BY stat_date DESC LIMIT ? OFFSET ?",
                memberId, safeSize, offset);
        return CommonResult.success(rows);
    }

    private boolean configBool(String key, boolean fallback) {
        try {
            List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                    (rs, n) -> rs.getString(1), key);
            if (!rows.isEmpty() && rows.get(0) != null) {
                String v = rows.get(0).trim();
                if ("1".equals(v) || "true".equalsIgnoreCase(v)) return true;
                if ("0".equals(v) || "false".equalsIgnoreCase(v)) return false;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private BigDecimal configDecimal(String key, BigDecimal fallback) {
        try {
            List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1",
                    (rs, n) -> rs.getString(1), key);
            if (!rows.isEmpty() && rows.get(0) != null) {
                BigDecimal v = new BigDecimal(rows.get(0).trim());
                if (v.signum() >= 0) return v;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    @Operation(summary = "查询分红池余额")
    @GetMapping("/pool")
    public CommonResult<Map<String, Object>> pool() {
        Map<String, Object> info = jdbc.queryForMap(
                "SELECT pool_balance,total_in,total_out,update_time FROM dividend_pool WHERE id=1");
        return CommonResult.success(info);
    }

    @Operation(summary = "记录贡献事件(可核验真实业务,幂等)")
    @PostMapping("/contribution")
    public CommonResult<Void> contribution(@RequestBody Map<String, Object> body) {
        return CommonResult.forbidden(null);
    }

    @Operation(summary = "模拟每日分红分配(基于贡献比例,幂等)")
    @PostMapping("/allocate")
    public CommonResult<Integer> allocate(@RequestBody Map<String, Object> body) {
        return CommonResult.forbidden(null);
    }

    @Operation(summary = "查询某日模拟分配快照")
    @GetMapping("/snapshot")
    public CommonResult<List<DividendSnapshot>> snapshot(@RequestParam Long statDate) {
        return CommonResult.success(dividendPoolService.getMySnapshot(new Date(statDate), memberService.getCurrentMember().getId()));
    }

    @Operation(summary = "查询我自己的模拟分配快照")
    @GetMapping("/mySnapshot")
    public CommonResult<List<DividendSnapshot>> mySnapshot(@RequestParam Long statDate) {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(dividendPoolService.getMySnapshot(new Date(statDate), memberId));
    }

    @Operation(summary = "查询某日模拟分配明细")
    @GetMapping("/detail")
    public CommonResult<List<DividendAllocateDetail>> detail(@RequestParam Long statDate) {
        return CommonResult.success(dividendPoolService.getAllocateDetail(new Date(statDate)).stream()
                .filter(item -> memberService.getCurrentMember().getId().equals(item.getMemberId()))
                .collect(java.util.stream.Collectors.toList()));
    }
}
