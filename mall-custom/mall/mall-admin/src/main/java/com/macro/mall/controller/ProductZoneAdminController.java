package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Brand is the existing PICK zone. Consumption remains reserved with no trading rules. */
@RestController
@RequestMapping("/p0/zones")
public class ProductZoneAdminController {
    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;

    public ProductZoneAdminController(JdbcTemplate jdbc, P0AdminAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    private void validateZone(String zone) {
        if (!"PICK".equals(zone) && !"REBUY".equals(zone) && !"CONSUME".equals(zone))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "专区无效");
    }

    @GetMapping("/{zone}/products")
    public CommonResult<List<Map<String, Object>>> products(Principal principal, @PathVariable String zone,
            @RequestParam(defaultValue="") String keyword,
            @RequestParam(defaultValue="false") boolean assignedOnly) {
        access.requireAuthorized(principal);
        validateZone(zone);
        if (keyword.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索词过长");
        String like = "%" + keyword.trim() + "%";
        return CommonResult.success(jdbc.queryForList(
                "SELECT p.id,p.name,p.pic,p.price,p.publish_status AS publishStatus," +
                "(z.product_id IS NOT NULL) AS assigned,COALESCE(z.sort_order,0) AS sortOrder " +
                "FROM pms_product p LEFT JOIN pms_product_zone z ON z.product_id=p.id AND z.zone_code=? " +
                "WHERE p.delete_status=0 AND (p.name LIKE ? OR p.product_sn LIKE ?) " +
                (assignedOnly ? "AND z.product_id IS NOT NULL " : "") +
                "ORDER BY (z.product_id IS NOT NULL) DESC,z.sort_order DESC,p.id DESC LIMIT 100", zone,like,like));
    }

    public record Assignment(@NotNull @Min(1) Long productId, @Min(-10000) @Max(10000) int sortOrder) {}

    @PostMapping("/{zone}/products")
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<Void> assign(Principal principal, @PathVariable String zone,
            @Valid @RequestBody Assignment body) {
        long operator = access.requireAuthorized(principal);
        validateZone(zone);
        // Product row lock serializes retries and concurrent zone edits for the same product.
        List<Long> products = jdbc.queryForList("SELECT id FROM pms_product WHERE id=? AND delete_status=0 FOR UPDATE",Long.class,body.productId());
        if (products.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"商品不存在");
        Integer incompatible = jdbc.queryForObject("SELECT COUNT(*) FROM pms_product_zone WHERE product_id=? AND "
                + ("CONSUME".equals(zone) ? "zone_code<>'CONSUME'" : "zone_code='CONSUME'"), Integer.class, body.productId());
        if (incompatible != null && incompatible > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"消费区商品不能与其他专区重复分配，请先移出原专区");
        List<Integer> old = jdbc.queryForList("SELECT sort_order FROM pms_product_zone WHERE product_id=? AND zone_code=?",Integer.class,body.productId(),zone);
        if (!old.isEmpty() && old.get(0)==body.sortOrder()) return CommonResult.success(null);
        if (old.isEmpty()) jdbc.update("INSERT INTO pms_product_zone(product_id,zone_code,sort_order) VALUES(?,?,?)",body.productId(),zone,body.sortOrder());
        else jdbc.update("UPDATE pms_product_zone SET sort_order=? WHERE product_id=? AND zone_code=?",body.sortOrder(),body.productId(),zone);
        audit(operator,principal.getName(),"assign",body.productId(),zone+" sort="+body.sortOrder());
        return CommonResult.success(null);
    }

    @DeleteMapping("/{zone}/products/{productId}")
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<Void> remove(Principal principal,@PathVariable String zone,@PathVariable long productId) {
        long operator=access.requireAuthorized(principal);
        validateZone(zone);
        jdbc.queryForList("SELECT id FROM pms_product WHERE id=? FOR UPDATE",Long.class,productId);
        int changed=jdbc.update("DELETE FROM pms_product_zone WHERE product_id=? AND zone_code=?",productId,zone);
        if(changed>0) audit(operator,principal.getName(),"remove",productId,zone);
        return CommonResult.success(null);
    }

    private void audit(long id,String name,String action,long productId,String detail) {
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) VALUES(?,?,?,?,?,?)",id,name,"product_zone",action,productId,detail);
    }
}
