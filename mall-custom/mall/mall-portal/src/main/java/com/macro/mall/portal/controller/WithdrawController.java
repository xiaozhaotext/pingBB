package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsWithdrawApply;
import com.macro.mall.portal.service.EarningsService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 提现（模拟）- 消费者端申请 / 后台审核处理
 * 注意：所有提现均为模拟状态流转，无真实打款。
 */
@RestController
@Tag(name = "WithdrawController", description = "提现申请与审核(模拟)")
@RequestMapping("/member/withdraw")
public class WithdrawController {

    @Autowired
    private EarningsService earningsService;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "申请提现(模拟)：扣减可提现并冻结，生成待审核单")
    @PostMapping("/apply")
    public CommonResult<UmsWithdrawApply> apply(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String idempotencyKey = String.valueOf(body.get("idempotencyKey"));
        return earningsService.applyWithdraw(memberId, amount, idempotencyKey);
    }

    @Operation(summary = "我的提现申请列表(模拟)")
    @GetMapping("/list")
    public CommonResult<List<UmsWithdrawApply>> list() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(earningsService.getWithdraws(memberId));
    }

    @Operation(summary = "后台处理提现(模拟)：approve=true完成入账 / false拒绝退回")
    @PostMapping("/process")
    public CommonResult<Void> process(@RequestBody Map<String, Object> body) {
        Long applyId = Long.parseLong(String.valueOf(body.get("applyId")));
        Long operatorId = memberService.getCurrentMember().getId();
        boolean approve = Boolean.parseBoolean(String.valueOf(body.get("approve")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return earningsService.processWithdraw(applyId, operatorId, approve, remark);
    }
}
