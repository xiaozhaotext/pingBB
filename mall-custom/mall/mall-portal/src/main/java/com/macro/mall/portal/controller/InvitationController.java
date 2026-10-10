package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.UmsInvitationCode;
import com.macro.mall.portal.domain.UmsMemberRecommend;
import com.macro.mall.portal.service.InvitationService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邀请码与推荐关系 - 消费者端
 */
@RestController
@Tag(name = "InvitationController", description = "邀请码与推荐关系")
@RequestMapping("/member/invitation")
public class InvitationController {

    @Autowired
    private InvitationService invitationService;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "获取我的邀请码(自动生成)")
    @GetMapping("/myCode")
    public CommonResult<String> myCode() {
        Long memberId = memberService.getCurrentMember().getId();
        return invitationService.ensureCode(memberId);
    }

    @Operation(summary = "我的邀请码详情")
    @GetMapping("/detail")
    public CommonResult<UmsInvitationCode> detail() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(invitationService.getMyCode(memberId));
    }

    @Operation(summary = "通过邀请码绑定推荐关系")
    @PostMapping("/bind")
    public CommonResult<UmsMemberRecommend> bind(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        String code = String.valueOf(body.get("code"));
        return invitationService.bind(memberId, code, memberId);
    }

    @Operation(summary = "我的推荐人")
    @GetMapping("/inviter")
    public CommonResult<UmsMemberRecommend> inviter() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(invitationService.getMyInviter(memberId));
    }

    @Operation(summary = "我邀请的下级")
    @GetMapping("/invitees")
    public CommonResult<List<UmsMemberRecommend>> invitees() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(invitationService.listMyInvitees(memberId));
    }
}
