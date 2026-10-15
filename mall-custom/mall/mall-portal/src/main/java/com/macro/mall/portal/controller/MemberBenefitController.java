package com.macro.mall.portal.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/member/benefits")
public class MemberBenefitController {
 private final JdbcTemplate jdbc;private final UmsMemberService members;
 public MemberBenefitController(JdbcTemplate jdbc,UmsMemberService members){this.jdbc=jdbc;this.members=members;}
 @GetMapping public CommonResult<List<Map<String,Object>>> mine(){return CommonResult.success(jdbc.queryForList("SELECT id,benefit_type,benefit_period,amount,status,created_at,reviewed_at FROM member_manual_benefit WHERE member_id=? ORDER BY id DESC LIMIT 200",members.getCurrentMember().getId()));}
}
