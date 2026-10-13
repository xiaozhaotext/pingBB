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
 * 在线咨询：用户提交咨询、查看自己的咨询及回复
 */
@RestController
@Tag(name = "ConsultationController", description = "在线咨询")
@RequestMapping("/member/consultation")
public class ConsultationController {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "提交咨询")
    @PostMapping("/submit")
    public CommonResult<Void> submit(@RequestBody Map<String, Object> body) {
        Long memberId = memberService.getCurrentMember().getId();
        String subject = body.get("subject") instanceof String ? (String) body.get("subject") : null;
        String content = body.get("content") instanceof String ? (String) body.get("content") : null;
        String contact = body.get("contact") == null ? null : String.valueOf(body.get("contact"));
        if (subject == null || subject.trim().isEmpty() || subject.length() > 128) {
            return CommonResult.failed("标题不能为空且不超过128字");
        }
        if (content == null || content.trim().isEmpty() || content.length() > 5000) {
            return CommonResult.failed("内容不能为空且不超过5000字");
        }
        if (contact != null && contact.length() > 128) return CommonResult.validateFailed("联系方式不能超过128字");
        jdbc.update("INSERT INTO sys_consultation(member_id,contact,subject,content,status)"
                        + " VALUES(?,?,?,?,'0')",
                memberId, contact, subject.trim(), content.trim());
        return CommonResult.success(null);
    }

    @Operation(summary = "我的咨询列表")
    @GetMapping("/my")
    public CommonResult<List<Map<String, Object>>> my() {
        Long memberId = memberService.getCurrentMember().getId();
        return CommonResult.success(jdbc.queryForList(
                "SELECT id,subject,content,status,reply,reply_time,create_time"
                        + " FROM sys_consultation WHERE member_id=? ORDER BY id DESC LIMIT 50",
                memberId));
    }
}
