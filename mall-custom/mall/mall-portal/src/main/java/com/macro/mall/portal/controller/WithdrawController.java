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
 * 佣金提现转入消费余额，不触发外部打款。
 */
@RestController
@Tag(name = "WithdrawController", description = "提现申请与审核(模拟)")
@RequestMapping("/member/withdraw")
public class WithdrawController {

    @Autowired
    private EarningsService earningsService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private com.macro.mall.portal.service.SandboxWalletService sandboxWallet;
    @Autowired
    private com.macro.mall.portal.service.WithdrawalPolicy withdrawalPolicy;

    @GetMapping("/options")
    public CommonResult<?> options() {
        memberService.getCurrentMember();
        return CommonResult.success(Map.of("sandbox",sandboxWallet.enabled(), "policy", withdrawalPolicy.options()));
    }

    @Operation(summary = "佣金提现：申请成功后全额转入消费余额")
    @PostMapping("/apply")
    public CommonResult<UmsWithdrawApply> apply(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        BigDecimal amount;
        try { amount = new BigDecimal(String.valueOf(body.get("amount"))); }
        catch (NumberFormatException ex) { return CommonResult.validateFailed("请输入有效的提现金额"); }
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
        return CommonResult.forbidden(null);
    }
}
