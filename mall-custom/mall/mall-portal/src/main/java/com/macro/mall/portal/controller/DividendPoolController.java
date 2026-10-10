package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.DividendAllocateDetail;
import com.macro.mall.portal.domain.DividendSnapshot;
import com.macro.mall.portal.service.DividendPoolService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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
    private UmsMemberService memberService;

    @Operation(summary = "记录贡献事件(可核验真实业务,幂等)")
    @PostMapping("/contribution")
    public CommonResult<Void> contribution(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        String eventType = String.valueOf(body.get("eventType"));
        String bizId = String.valueOf(body.get("bizId"));
        BigDecimal value = new BigDecimal(String.valueOf(body.get("contributionValue")));
        Date statDate = new Date(Long.parseLong(String.valueOf(body.get("statDate"))));
        return dividendPoolService.recordContributionEvent(memberId, eventType, bizId, value, statDate);
    }

    @Operation(summary = "模拟每日分红分配(基于贡献比例,幂等)")
    @PostMapping("/allocate")
    public CommonResult<Integer> allocate(@RequestBody Map<String, Object> body) {
        Date statDate = new Date(Long.parseLong(String.valueOf(body.get("statDate"))));
        BigDecimal poolTotal = body.get("poolTotal") == null ? null : new BigDecimal(String.valueOf(body.get("poolTotal")));
        String calcBatch = String.valueOf(body.get("calcBatch"));
        return dividendPoolService.simulateDailyAllocate(statDate, poolTotal, calcBatch);
    }

    @Operation(summary = "查询某日模拟分配快照")
    @GetMapping("/snapshot")
    public CommonResult<List<DividendSnapshot>> snapshot(@RequestParam Long statDate) {
        return CommonResult.success(dividendPoolService.getSnapshot(new Date(statDate)));
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
        return CommonResult.success(dividendPoolService.getAllocateDetail(new Date(statDate)));
    }
}
