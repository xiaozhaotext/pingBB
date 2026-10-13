package com.macro.mall.portal.controller;
import com.macro.mall.common.api.CommonResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/home/site-profile")
public class SiteProfilePublicController {
    private final JdbcTemplate jdbc;
    public SiteProfilePublicController(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @GetMapping("/company")
    public CommonResult<Map<String,Object>> company() {
        return CommonResult.success(jdbc.queryForMap("SELECT company_name,address,telephone,mobile,email,qq,wechat_qr_url FROM sys_site_profile WHERE id=1"));
    }
    @GetMapping("/privacy")
    public CommonResult<Map<String,Object>> privacy() {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT published_title AS title,published_content AS content,published_revision AS revision,published_at FROM sys_site_profile WHERE id=1 AND published_revision IS NOT NULL");
        return rows.isEmpty()?CommonResult.failed("隐私政策尚未发布"):CommonResult.success(rows.get(0));
    }
}
