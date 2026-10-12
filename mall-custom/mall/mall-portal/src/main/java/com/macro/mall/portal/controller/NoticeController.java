package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private JdbcTemplate jdbc;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "获取通知列表（含已读状态）")
    @GetMapping("/list")
    public CommonResult<List<Map<String, Object>>> list() {
        Long memberId = memberService.getCurrentMember().getId();
        List<Map<String, Object>> notices = jdbc.queryForList(
                "SELECT n.id,n.title,n.content,n.type,n.publish_time,"
                        + " CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS is_read"
                        + " FROM sys_notice n LEFT JOIN sys_notice_read r"
                        + " ON r.notice_id=n.id AND r.member_id=?"
                        + " WHERE n.status=1 ORDER BY n.publish_time DESC LIMIT 50",
                memberId);
        return CommonResult.success(notices);
    }

    @Operation(summary = "标记通知已读")
    @PostMapping("/read")
    public CommonResult<Void> markRead(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        Long noticeId = Long.parseLong(String.valueOf(body.get("noticeId")));
        jdbc.update("INSERT IGNORE INTO sys_notice_read(notice_id,member_id) VALUES(?,?)",
                noticeId, memberId);
        return CommonResult.success(null);
    }

    @Operation(summary = "获取未读数量")
    @GetMapping("/unreadCount")
    public CommonResult<Integer> unreadCount() {
        Long memberId = memberService.getCurrentMember().getId();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_notice n WHERE n.status=1"
                        + " AND NOT EXISTS(SELECT 1 FROM sys_notice_read r"
                        + " WHERE r.notice_id=n.id AND r.member_id=?)",
                Integer.class, memberId);
        return CommonResult.success(count == null ? 0 : count);
    }
}
