package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.MembershipTierService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Consumer-facing four-tier identity: tourist/member/partner/cofounder.
 */
@RestController
@Tag(name = "MemberIdentityController", description = "会员身份(游客/会员/合伙人/联创)")
@RequestMapping("/member/identity")
public class MemberIdentityController {

    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private MembershipTierService tierService;

    @Operation(summary = "查询我的当前身份")
    @GetMapping("/current")
    public CommonResult<Map<String, Object>> current() {
        Long memberId = memberService.getCurrentMember().getId();
        var tier = tierService.tier(memberId);
        return CommonResult.success(Map.of("memberId", memberId, "identityType", tier.code(),
                "identityName", tier.name(), "partnerShares", tier.partnerShares(),
                "cofounderShares", tier.cofounderShares()));
    }

    @Operation(summary = "消费者端不允许直接变更身份")
    @PostMapping("/change")
    public CommonResult<Void> change(@RequestBody Map<String, Object> body) {
        return CommonResult.forbidden(null);
    }
}
