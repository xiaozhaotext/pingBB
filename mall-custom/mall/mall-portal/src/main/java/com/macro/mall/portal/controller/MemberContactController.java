package com.macro.mall.portal.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/member/contact")
public class MemberContactController {
 private final JdbcTemplate jdbc;private final UmsMemberService members;
 public MemberContactController(JdbcTemplate jdbc,UmsMemberService members){this.jdbc=jdbc;this.members=members;}
 @GetMapping public CommonResult<Map<String,Object>> get(){var rows=jdbc.queryForList("SELECT real_name,email,wechat_account FROM ums_member_contact WHERE member_id=?",members.getCurrentMember().getId());return CommonResult.success(rows.isEmpty()?Map.of("real_name","","email","","wechat_account",""):rows.get(0));}
 @PostMapping @Transactional(rollbackFor=Exception.class)
 public CommonResult<?> save(@RequestBody Map<String,Object> body){
  String name=String.valueOf(body.getOrDefault("real_name","")).trim(),email=String.valueOf(body.getOrDefault("email","")).trim(),wechat=String.valueOf(body.getOrDefault("wechat_account","")).trim();
  if(name.length()>64 || email.length()>128 || wechat.length()>64 || (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))return CommonResult.failed("请检查姓名、邮箱和微信账号格式");
  jdbc.update("INSERT INTO ums_member_contact(member_id,real_name,email,wechat_account) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE real_name=VALUES(real_name),email=VALUES(email),wechat_account=VALUES(wechat_account)",members.getCurrentMember().getId(),name,email,wechat);return CommonResult.success(null);
 }
}
