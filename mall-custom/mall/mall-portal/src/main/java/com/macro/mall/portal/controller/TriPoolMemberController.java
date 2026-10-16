package com.macro.mall.portal.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/member/tri-pools")
public class TriPoolMemberController {
    private final TriPoolService pools; private final UmsMemberService members; private final JdbcTemplate jdbc;
    public TriPoolMemberController(TriPoolService pools,UmsMemberService members,JdbcTemplate jdbc) {
        this.pools=pools;this.members=members;this.jdbc=jdbc;
    }
    @GetMapping
    public CommonResult<Map<String,Object>> summary() { return CommonResult.success(pools.memberSummary(members.getCurrentMember().getId())); }
    @GetMapping("/records")
    public CommonResult<List<Map<String,Object>>> records(@RequestParam(defaultValue="1")int page) {
        long id=members.getCurrentMember().getId();
        if(!pools.enabled())return CommonResult.success(List.of());
        if(page<1 || page>1000000)throw new IllegalArgumentException("页码无效");
        return CommonResult.success(jdbc.queryForList("SELECT id,batch_id,pool_code,identity_snapshot,shares,total_shares,cap_amount,received_before,amount,created_at FROM tri_pool_distribution WHERE member_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",id,(page-1)*20));
    }
    @PostMapping("/travel")
    public CommonResult<Void> travel(@RequestBody TravelRequest request) {
        long id=members.getCurrentMember().getId();
        if(!pools.isMember(id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"旅游路线定制仅限会员及以上身份");
        if(request.route==null || request.route.isBlank() || request.route.length()>500
                || request.days<1 || request.days>90 || request.people<1 || request.people>100
                || request.preferences==null || request.preferences.length()>2000
                || request.contact==null || request.contact.isBlank() || request.contact.length()>128)
            throw new IllegalArgumentException("请填写路线、1至90天行程、1至100人及有效联系方式");
        String content="路线："+request.route.trim()+"\n行程天数："+request.days+"\n人数："+request.people
                +"\n偏好："+request.preferences.trim();
        jdbc.update("INSERT INTO sys_consultation(member_id,contact,subject,content,status) VALUES(?,?,'会员旅游路线定制',?,'0')",id,request.contact.trim(),content);
        return CommonResult.success(null);
    }
    public record TravelRequest(String route,int days,int people,String preferences,String contact) { }
}
