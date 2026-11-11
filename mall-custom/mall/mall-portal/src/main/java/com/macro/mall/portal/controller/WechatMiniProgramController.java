package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.WechatMiniProgramService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/member/wechat-mini")
public class WechatMiniProgramController {
    private final UmsMemberService members;
    private final WechatMiniProgramService mini;

    public WechatMiniProgramController(UmsMemberService members, WechatMiniProgramService mini) {
        this.members = members;
        this.mini = mini;
    }

    public record LoginRequest(@NotBlank @Size(max = 256) String code) {}

    @PostMapping("/login")
    public CommonResult<WechatMiniProgramService.Authorization> login(@Valid @RequestBody LoginRequest request) {
        return CommonResult.success(mini.login(members.getCurrentMember().getId(), request.code()));
    }
}
