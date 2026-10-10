package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.UmsMemberIdentity;
import com.macro.mall.portal.service.MemberIdentityService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 会员身份（普通会员=0 / 合伙人=1 / 联创=2）- 消费者端
 */
@RestController
@Tag(name = "MemberIdentityController", description = "会员身份(普通/合伙人/联创)")
@RequestMapping("/member/identity")
public class MemberIdentityController {

    @Autowired
    private MemberIdentityService identityService;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "查询我的当前身份")
    @GetMapping("/current")
    public CommonResult<UmsMemberIdentity> current() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(identityService.getIdentity(memberId));
    }

    @Operation(summary = "后台/运营变更会员身份(必须写变更审计)")
    @PostMapping("/change")
    public CommonResult<Void> change(@RequestBody Map<String, Object> body) {
        Long memberId = Long.parseLong(String.valueOf(body.get("memberId")));
        int newType = Integer.parseInt(String.valueOf(body.get("newType")));
        String reason = body.get("reason") == null ? "运营调整" : String.valueOf(body.get("reason"));
        Long operatorId = memberService.getCurrentMember().getId();
        Long bizOrderId = body.get("bizOrderId") == null ? null : Long.parseLong(String.valueOf(body.get("bizOrderId")));
        return identityService.changeIdentity(memberId, newType, operatorId, reason, bizOrderId);
    }
}
