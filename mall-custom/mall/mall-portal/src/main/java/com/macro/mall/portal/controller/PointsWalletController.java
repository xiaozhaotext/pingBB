package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.UmsPointsAccount;
import com.macro.mall.portal.domain.UmsPointsLedger;
import com.macro.mall.portal.service.MemberService;
import com.macro.mall.portal.service.PointsWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 双积分钱包 - 消费者端
 */
@RestController
@Tag(name = "PointsWalletController", description = "双积分钱包(商品积分/复购积分)")
@RequestMapping("/member/points")
public class PointsWalletController {

    @Autowired
    private PointsWalletService pointsWalletService;
    @Autowired
    private MemberService memberService;

    @Operation(summary = "查询某类积分账户")
    @GetMapping("/account")
    public CommonResult<UmsPointsAccount> account(@RequestParam Integer pointsType) {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(pointsWalletService.getAccount(memberId, pointsType));
    }

    @Operation(summary = "查询积分流水")
    @GetMapping("/ledger")
    public CommonResult<List<UmsPointsLedger>> ledger(@RequestParam(required = false) Integer pointsType) {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(pointsWalletService.getLedger(memberId, pointsType));
    }

    @Operation(summary = "积分变动(发放/消费/调整/分红)，幂等")
    @PostMapping("/change")
    public CommonResult<Void> change(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        int pointsType = Integer.parseInt(String.valueOf(body.get("pointsType")));
        String changeType = String.valueOf(body.get("changeType"));
        long amount = Long.parseLong(String.valueOf(body.get("amount")));
        String bizId = body.get("bizId") == null ? null : String.valueOf(body.get("bizId"));
        String idempotencyKey = String.valueOf(body.get("idempotencyKey"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return pointsWalletService.changePoints(memberId, pointsType, changeType, amount, bizId, idempotencyKey, memberId, remark);
    }

    @Operation(summary = "冻结积分")
    @PostMapping("/freeze")
    public CommonResult<Void> freeze(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        int pointsType = Integer.parseInt(String.valueOf(body.get("pointsType")));
        long amount = Long.parseLong(String.valueOf(body.get("amount")));
        String idempotencyKey = String.valueOf(body.get("idempotencyKey"));
        return pointsWalletService.freeze(memberId, pointsType, amount, idempotencyKey, memberId, String.valueOf(body.get("remark")));
    }

    @Operation(summary = "解冻积分")
    @PostMapping("/unfreeze")
    public CommonResult<Void> unfreeze(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        int pointsType = Integer.parseInt(String.valueOf(body.get("pointsType")));
        long amount = Long.parseLong(String.valueOf(body.get("amount")));
        String idempotencyKey = String.valueOf(body.get("idempotencyKey"));
        return pointsWalletService.unfreeze(memberId, pointsType, amount, idempotencyKey, memberId, String.valueOf(body.get("remark")));
    }
}
