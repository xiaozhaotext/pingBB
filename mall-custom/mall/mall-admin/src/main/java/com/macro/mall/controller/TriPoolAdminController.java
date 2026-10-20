package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.common.membership.TriPoolRules;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.TriPoolDailyReportService;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/p0/tri-pools")
public class TriPoolAdminController {
    private final JdbcTemplate jdbc; private final TriPoolService pools; private final P0AdminAccess access;
    @org.springframework.beans.factory.annotation.Autowired private TriPoolDailyReportService dailyReports;
    public TriPoolAdminController(JdbcTemplate jdbc,TriPoolService pools,P0AdminAccess access) {
        this.jdbc=jdbc;this.pools=pools;this.access=access;
    }
    @GetMapping("/daily")
    public CommonResult<Map<String,Object>> daily(Principal p,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        access.requireAuthorized(p);
        if(!pools.enabled())return CommonResult.success(Map.of("enabled",false));
        return CommonResult.success(dailyReports.daily(date));
    }
    @GetMapping
    public CommonResult<Map<String,Object>> overview(Principal p) {
        access.requireAuthorized(p);
        if(!pools.enabled())return CommonResult.success(Map.of("enabled",false));
        return CommonResult.success(Map.of("enabled",true,"ruleVersion",TriPoolRules.VERSION,
                "pools",jdbc.queryForList("SELECT * FROM tri_pool ORDER BY code"),
                "operationsMembers",jdbc.queryForList("SELECT o.*,m.username FROM tri_operations_member o JOIN ums_member m ON m.id=o.member_id ORDER BY o.member_id"),
                "membershipPrice",TriPoolRules.PRICE));
    }
    @GetMapping("/records")
    public CommonResult<List<Map<String,Object>>> records(Principal p,@RequestParam String pool,
                                                         @RequestParam(defaultValue="1")int page,
                                                         @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        access.requireAuthorized(p); TriPoolRules.Pool.valueOf(pool);
        if(!pools.enabled())return CommonResult.success(List.of());
        if(page<1 || page>1000000)throw new IllegalArgumentException("页码无效");
        String query="SELECT d.*,m.username,b.source_key,b.operator_id FROM tri_pool_distribution d JOIN tri_pool_batch b ON b.id=d.batch_id LEFT JOIN ums_member m ON m.id=d.member_id WHERE d.pool_code=?";
        var args=new ArrayList<Object>();args.add(pool);
        if(date!=null) { query+=" AND d.created_at>=? AND d.created_at<?";args.add(dailyReports.start(date));args.add(dailyReports.start(date.plusDays(1))); }
        args.add((page-1)*50);
        return CommonResult.success(jdbc.queryForList(query+" ORDER BY d.id DESC LIMIT 50 OFFSET ?",args.toArray()));
    }
    @GetMapping("/ledger")
    public CommonResult<List<Map<String,Object>>> ledger(Principal p,@RequestParam String pool,
                                                        @RequestParam(defaultValue="1")int page,
                                                        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        access.requireAuthorized(p);TriPoolRules.Pool.valueOf(pool);
        if(!pools.enabled())return CommonResult.success(List.of());
        if(page<1 || page>1000000)throw new IllegalArgumentException("页码无效");
        String query="SELECT * FROM tri_pool_ledger WHERE pool_code=?";
        var args=new ArrayList<Object>();args.add(pool);
        if(date!=null) { query+=" AND created_at>=? AND created_at<?";args.add(dailyReports.start(date));args.add(dailyReports.start(date.plusDays(1))); }
        args.add((page-1)*50);
        return CommonResult.success(jdbc.queryForList(query+" ORDER BY id DESC LIMIT 50 OFFSET ?",args.toArray()));
    }
    @GetMapping("/members")
    public CommonResult<List<Map<String,Object>>> members(Principal p,@RequestParam(defaultValue="1")int page) {
        access.requireAuthorized(p);
        if(!pools.enabled())return CommonResult.success(List.of());
        if(page<1 || page>1000000)throw new IllegalArgumentException("页码无效");
        var rows=jdbc.queryForList("SELECT u.id,u.username,COALESCE(m.tier,'VISITOR') AS tier,COALESCE(m.direct_paid_count,0) AS direct_paid_count,m.joined_at FROM ums_member u LEFT JOIN tri_member m ON m.member_id=u.id ORDER BY u.id DESC LIMIT 50 OFFSET ?",(page-1)*50);
        for(var row:rows) {
            boolean paid=!"VISITOR".equals(row.get("tier"));long refs=((Number)row.get("direct_paid_count")).longValue();
            long units=paid?pools.membershipUnits(((Number)row.get("id")).longValue()):0;
            row.put("membershipUnits",units);
            row.put("partnerShares",paid?TriPoolRules.membershipShares(refs,units):0);
            var summary=pools.memberSummary(((Number)row.get("id")).longValue());
            var positions=(List<Map<String,Object>>)summary.get("pools");
            row.put("partnerCap",positions.get(0).get("cap"));
            row.put("cofounderCap",positions.get(1).get("cap"));
            row.put("totalCap",summary.get("totalCap"));
        }
        return CommonResult.success(rows);
    }
    @GetMapping("/operations/preview")
    public CommonResult<Map<String,Object>> preview(Principal p) {
        access.requireAuthorized(p);return CommonResult.success(pools.operationsPreview());
    }
    @PostMapping("/operations/members")
    public CommonResult<?> operationsMember(Principal p,@RequestBody OperationsMember body) {
        long operator=access.requireAuthorized(p);
        pools.setOperationsMember(body.memberId,body.shares,body.enabled,operator,p.getName(),body.reason);
        return CommonResult.success(null);
    }
    public record OperationsMember(long memberId,long shares,boolean enabled,String reason) { }
    @PostMapping("/operations/distribute")
    public CommonResult<Map<String,Object>> distribute(Principal p,@RequestBody Distribution body) {
        long operator=access.requireAuthorized(p);
        return CommonResult.success(pools.distributeOperations(body.requestId,body.balance,body.totalShares,body.previewToken,operator,p.getName()));
    }
    public record Distribution(String requestId,BigDecimal balance,long totalShares,String previewToken) { }

}
