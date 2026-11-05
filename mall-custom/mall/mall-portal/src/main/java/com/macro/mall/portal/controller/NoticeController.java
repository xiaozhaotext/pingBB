package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import com.macro.mall.portal.service.NoticeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息通知：用户端查询通知列表、标记已读
 */
@RestController
@Tag(name = "NoticeController", description = "消息通知")
@RequestMapping("/member/notice")
public class NoticeController {

    @Autowired
    private NoticeService notices;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "获取通知列表（含已读状态）")
    @GetMapping("/list")
    public CommonResult<List<Map<String, Object>>> list() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(notices.memberNotices(memberId));
    }

    @Operation(summary = "标记通知已读")
    @PostMapping("/read")
    public CommonResult<Void> markRead(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        Long noticeId = Long.parseLong(String.valueOf(body.get("noticeId")));
        notices.markRead(noticeId, memberId);
        return CommonResult.success(null);
    }

    @Operation(summary = "获取未读数量")
    @GetMapping("/unreadCount")
    public CommonResult<Integer> unreadCount() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(notices.unreadCount(memberId));
    }
}
