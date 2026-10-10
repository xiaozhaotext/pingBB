package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.BizPartnerConfig;
import com.macro.mall.portal.domain.BizPartnerOrder;
import com.macro.mall.portal.service.MemberService;
import com.macro.mall.portal.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 合伙人权益（模拟）- 消费者端
 * 注意：所有支付与奖励均为模拟，前端须明确标注"模拟"，不接入真实资金。
 */
@RestController
@Tag(name = "PartnerController", description = "合伙人权益(模拟)")
@RequestMapping("/member/partner")
public class PartnerController {

    @Autowired
    private PartnerService partnerService;
    @Autowired
    private MemberService memberService;

    @Operation(summary = "获取合伙人权益方案(模拟)")
    @GetMapping("/config")
    public CommonResult<BizPartnerConfig> config() {
        return CommonResult.success(partnerService.getConfig());
    }

    @Operation(summary = "创建模拟购买订单(幂等键bizId)")
    @PostMapping("/order")
    public CommonResult<BizPartnerOrder> order(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        String bizId = String.valueOf(body.get("bizId"));
        return partnerService.createSimulateOrder(memberId, bizId);
    }

    @Operation(summary = "模拟支付并升级为合伙人(事务+幂等)")
    @PostMapping("/pay")
    public CommonResult<Void> pay(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        String bizId = String.valueOf(body.get("bizId"));
        return partnerService.confirmSimulatePay(memberId, bizId);
    }
}
