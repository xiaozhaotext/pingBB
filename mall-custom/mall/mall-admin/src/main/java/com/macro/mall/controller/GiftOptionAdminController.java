package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/p0/gift-options")
public class GiftOptionAdminController {
    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;
    public GiftOptionAdminController(JdbcTemplate jdbc,P0AdminAccess access){this.jdbc=jdbc;this.access=access;}
    @GetMapping public CommonResult<List<Map<String,Object>>> list(Principal p){
        access.requireAuthorized(p);
        return CommonResult.success(jdbc.queryForList("SELECT id,name,image_url AS imageUrl,enabled,sort_order AS sortOrder FROM oms_gift_option ORDER BY id"));
    }
    @GetMapping("/order/{orderId}") public CommonResult<List<Map<String,Object>>> orderGifts(Principal p,@PathVariable long orderId){
        access.requireAuthorized(p);
        return CommonResult.success(jdbc.queryForList("SELECT id,name_snapshot AS name,image_snapshot AS imageUrl FROM oms_order_gift WHERE order_id=? ORDER BY id",orderId));
    }
    public record GiftOption(long id,String name,String imageUrl,boolean enabled){}
    @PostMapping public CommonResult<?> save(Principal p,@RequestBody GiftOption gift){
        long operator=access.requirePlatformAdmin(p);
        if(gift==null || gift.id()<1 || gift.id()>4 || gift.name()==null || gift.name().trim().isEmpty() || gift.name().length()>120
                || gift.imageUrl()==null || gift.imageUrl().length()>500
                || !(gift.imageUrl().startsWith("/product-images/") || gift.imageUrl().startsWith("/oss/")))
            throw new IllegalArgumentException("请填写赠品名称并上传图片；只支持4个赠品位");
        jdbc.update("INSERT INTO oms_gift_option(id,name,image_url,enabled,sort_order) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),image_url=VALUES(image_url),enabled=VALUES(enabled)",gift.id(),gift.name().trim(),gift.imageUrl(),gift.enabled()?1:0,gift.id());
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'gift_option','SAVE',?)",p.getName(),operator,"slot="+gift.id()+";enabled="+gift.enabled());
        return CommonResult.success(null);
    }
}
