package com.macro.mall.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.YearMonth;
import java.util.*;

/** Explicit administrative requests and review; approval credits earnings, never initiates a bank payout. */
@RestController @RequestMapping("/p0/benefits")
public class ManualBenefitController {
 private final JdbcTemplate jdbc; private final P0AdminAccess access;
 public ManualBenefitController(JdbcTemplate jdbc,P0AdminAccess access){this.jdbc=jdbc;this.access=access;}
 @GetMapping public CommonResult<List<Map<String,Object>>> list(Principal principal){access.requireAuthorized(principal);return CommonResult.success(jdbc.queryForList("SELECT * FROM member_manual_benefit ORDER BY id DESC LIMIT 200"));}
 @PostMapping @Transactional(rollbackFor=Exception.class)
 public CommonResult<?> request(Principal principal,@RequestBody Map<String,Object> body){
  long operator=access.requireAuthorized(principal);
  if(!legacyBenefitsEnabled())return CommonResult.failed("旧版人工权益申请已关闭");
  long member; try {member=Long.parseLong(String.valueOf(body.get("memberId")));}catch(Exception e){return CommonResult.failed("会员编号无效");}
  String type=String.valueOf(body.get("type"));String reason=String.valueOf(body.getOrDefault("reason","" )).trim();
  if(reason.length()<5 || reason.length()>500)return CommonResult.failed("申请依据需为5至500字");
  var identities=jdbc.queryForList("SELECT identity_type FROM ums_member_identity WHERE member_id=? AND status=1 FOR UPDATE",Integer.class,member);
  if(identities.isEmpty())return CommonResult.failed("该会员无有效会员权益");
  String period;BigDecimal amount;
  if("MONTHLY_REWARD".equals(type)){
   if(identities.get(0)!=2)return CommonResult.failed("每月200元奖励仅适用于联创");
   try{var month=YearMonth.parse(String.valueOf(body.get("period")));if(month.isAfter(YearMonth.now()))return CommonResult.failed("不可提前发放未来月份奖励");period=month.toString();}catch(Exception e){return CommonResult.failed("月份格式应为YYYY-MM");}
   amount=new BigDecimal("200.00");
  }else if("TRAVEL".equals(type)){
   if(identities.get(0)!=1 && identities.get(0)!=2)return CommonResult.failed("该会员无旅游权益");
   period="ONCE";amount=BigDecimal.ZERO;
  }else return CommonResult.failed("未知权益类型");
  var existing=jdbc.queryForList("SELECT status FROM member_manual_benefit WHERE member_id=? AND benefit_type=? AND benefit_period=?",String.class,member,type,period);
  if(!existing.isEmpty())return CommonResult.failed("该月份或一次性权益已申请，请查看原记录");
  jdbc.update("INSERT INTO member_manual_benefit(member_id,benefit_type,benefit_period,amount,request_reason,requested_by) VALUES(?,?,?,?,?,?)",member,type,period,amount,reason,operator);
  Long requestId=jdbc.queryForObject("SELECT id FROM member_manual_benefit WHERE member_id=? AND benefit_type=? AND benefit_period=?",Long.class,member,type,period);
  audit(operator,principal.getName(),"REQUEST",requestId,"会员 "+member+"，权益 "+type+"，期间 "+period+"；"+reason);
  return CommonResult.success(null);
 }
 @PostMapping("/{id}/review") @Transactional(rollbackFor=Exception.class)
 public CommonResult<?> review(Principal principal,@PathVariable long id,@RequestBody Map<String,Object> body){
  long operator=access.requireAuthorized(principal);
  if(!legacyBenefitsEnabled())return CommonResult.failed("旧版人工权益审核已关闭");
  String note=String.valueOf(body.getOrDefault("note","")).trim();
  if(note.length()<5 || note.length()>500)return CommonResult.failed("审核或核销凭据需为5至500字");
  if(!(body.get("approve") instanceof Boolean))return CommonResult.failed("请选择审核结果");
  boolean approve=Boolean.TRUE.equals(body.get("approve"));
  var rows=jdbc.queryForList("SELECT * FROM member_manual_benefit WHERE id=? FOR UPDATE",id);
  if(rows.isEmpty())return CommonResult.failed("权益申请不存在");var row=rows.get(0);
  if(!"PENDING".equals(row.get("status")))return CommonResult.failed("该申请已处理");
  long member=((Number)row.get("member_id")).longValue();
  if(approve && "MONTHLY_REWARD".equals(row.get("benefit_type"))){
   var eligible=jdbc.queryForList("SELECT member_id FROM ums_member_identity WHERE member_id=? AND status=1 AND identity_type=2 FOR UPDATE",Long.class,member);
   if(eligible.isEmpty())return CommonResult.failed("该会员已无有效联创身份");
   BigDecimal amount=(BigDecimal)row.get("amount");String key="manual-monthly:"+id;
   jdbc.update("INSERT INTO ums_earnings(member_id,total_earnings,available_earnings,frozen_earnings,withdrawn,version) VALUES(?,0,0,0,0,0) ON DUPLICATE KEY UPDATE member_id=member_id",member);
   jdbc.queryForObject("SELECT id FROM ums_earnings WHERE member_id=? FOR UPDATE",Long.class,member);
   jdbc.update("UPDATE ums_earnings SET available_earnings=available_earnings+?,total_earnings=total_earnings+?,version=version+1 WHERE member_id=?",amount,amount,member);
   jdbc.update("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,biz_id,idempotency_key,remark) SELECT member_id,'monthly_reward',?,available_earnings,?,?,? FROM ums_earnings WHERE member_id=?",amount,Long.toString(id),key,"人工审核每月奖励 "+row.get("benefit_period")+"；"+note,member);
  }
  jdbc.update("UPDATE member_manual_benefit SET status=?,reviewed_by=?,review_note=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",approve?("TRAVEL".equals(row.get("benefit_type"))?"REDEEMED":"APPROVED"):"REJECTED",operator,note,id);
  audit(operator,principal.getName(),approve?"APPROVE":"REJECT",id,"会员 "+member+"；"+note);
  return CommonResult.success(null);
 }
 private void audit(long operator,String name,String action,long id,String detail){
  jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) VALUES(?,?,?,?,?,?)",operator,name,"manual_benefit",action,id,detail);
 }
 private boolean legacyBenefitsEnabled(){
  var values=jdbc.queryForList("SELECT config_value FROM sys_config WHERE config_key='legacy.manual_benefits.enabled'",String.class);
  return !values.isEmpty() && "1".equals(values.get(0));
 }
}
