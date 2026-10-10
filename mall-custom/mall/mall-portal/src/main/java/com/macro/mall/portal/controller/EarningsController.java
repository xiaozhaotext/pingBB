package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsEarnings;
import com.macro.mall.portal.domain.UmsEarningsLedger;
import com.macro.mall.portal.service.EarningsService;
import com.macro.mall.portal.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 会员收益（模拟）- 消费者端
 * 所有金额均为模拟，不得展示为真实可提现资产。
 */
@RestController
@Tag(name = "EarningsController", description = "会员收益账户(模拟)")
@RequestMapping("/member/earnings")
public class EarningsController {

    @Autowired
    private EarningsService earningsService;
    @Autowired
    private MemberService memberService;

    @Operation(summary = "查询我的收益账户(模拟)")
    @GetMapping("/account")
    public CommonResult<UmsEarnings> account() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(earningsService.getAccount(memberId));
    }

    @Operation(summary = "查询收益流水(模拟)")
    @GetMapping("/ledger")
    public CommonResult<List<UmsEarningsLedger>> ledger() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(earningsService.getLedger(memberId));
    }

    @Operation(summary = "模拟发放收益(幂等,仅演示/测试用)")
    @PostMapping("/simulateReward")
    public CommonResult<Void> simulateReward(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String bizId = body.get("bizId") == null ? null : String.valueOf(body.get("bizId"));
        String idempotencyKey = String.valueOf(body.get("idempotencyKey"));
        String remark = body.get("remark") == null ? "模拟发放(演示)" : String.valueOf(body.get("remark"));
        return earningsService.simulateReward(memberId, amount, bizId, idempotencyKey, remark);
    }
}
