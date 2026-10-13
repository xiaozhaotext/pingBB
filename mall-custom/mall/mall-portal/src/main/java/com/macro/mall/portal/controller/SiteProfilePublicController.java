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
        Map<String,Object> result=new java.util.LinkedHashMap<>();
        String[][] fields={{"company.name","company_name"},{"company.address","address"},{"company.phone","telephone"},{"company.mobile","mobile"},{"company.email","email"},{"company.qq","qq"},{"company.wechat_qrcode","wechat_qr_url"}};
        for(String[] field:fields) {
            List<String> values=jdbc.query("SELECT config_value FROM sys_config WHERE config_key=?",(rs,n)->rs.getString(1),field[0]);
            result.put(field[1],values.isEmpty() || values.get(0)==null?"":values.get(0));
        }
        return CommonResult.success(result);
    }
    @GetMapping("/privacy")
    public CommonResult<Map<String,Object>> privacy() {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT published_title AS title,published_content AS content,published_revision AS revision,published_at FROM sys_site_profile WHERE id=1 AND published_revision IS NOT NULL");
        return rows.isEmpty()?CommonResult.failed("隐私政策尚未发布"):CommonResult.success(rows.get(0));
    }
}
