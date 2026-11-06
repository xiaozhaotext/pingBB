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
import java.math.BigDecimal;

/** 自选区(PICK)、复购区(REBUY)、消费区(CONSUME)商品归属管理，每件商品只归属一个专区。 */
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
        String pickUnavailable = "PICK".equals(zone)
                ? " WHEN p.price<>3999.00 OR t.product_id IS NULL OR t.enabled<>1 "
                    + "OR NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price=3999.00) "
                    + "OR EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND (s.price IS NULL OR s.price<>3999.00)) "
                    + "THEN '旅游路线或规格价格不符合3999元规则' "
                : "";
        return CommonResult.success(jdbc.queryForList(
                "SELECT p.id,p.name,p.pic,p.price,p.publish_status AS publishStatus," +
                "(t.product_id IS NOT NULL AND t.enabled=1) AS tourRoute," +
                "(z.product_id IS NOT NULL) AS assigned,COALESCE(z.sort_order,0) AS sortOrder " +
                ",CASE WHEN z.product_id IS NULL THEN '未加入当前专区' " +
                "WHEN p.publish_status<>1 THEN '商品未上架' " +
                "WHEN b.id IS NOT NULL AND b.show_status<>1 THEN '所属品牌未展示' " +
                pickUnavailable + "ELSE '' END AS displayReason " +
                "FROM pms_product p LEFT JOIN pms_product_zone z ON z.product_id=p.id AND z.zone_code=? " +
                "LEFT JOIN tri_membership_product t ON t.product_id=p.id " +
                "LEFT JOIN pms_brand b ON b.id=p.brand_id " +
                "WHERE p.delete_status=0 AND (p.name LIKE ? OR p.product_sn LIKE ?) " +
                "AND z.product_id IS NOT NULL " +
                "ORDER BY (z.product_id IS NOT NULL) DESC,z.sort_order DESC,p.id DESC", zone,like,like));
    }

    public record BatchPublish(List<Long> ids) {}
    @PostMapping("/{zone}/products/publish")
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<?> publish(Principal principal,@PathVariable String zone,@RequestBody BatchPublish body) {
        long operator=access.requireAuthorized(principal);validateZone(zone);
        if(body.ids()==null || body.ids().isEmpty() || body.ids().stream().anyMatch(id->id==null || id<=0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择有效商品");
        var ids=body.ids().stream().distinct().sorted().toList();
        for(long id:ids) {
            var found=jdbc.queryForList("SELECT p.id FROM pms_product p JOIN pms_product_zone z ON z.product_id=p.id WHERE p.id=? AND z.zone_code=? AND p.delete_status=0 FOR UPDATE",Long.class,id,zone);
            if(found.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"所选商品不属于当前专区或已删除，请刷新列表");
        }
        int changed=0;
        for(long id:ids) {
            changed+=jdbc.update("UPDATE pms_product SET publish_status=1 WHERE id=? AND delete_status=0 AND publish_status<>1",id);
            audit(operator,principal.getName(),"batch_publish",id,zone);
        }
        return CommonResult.success(changed);
    }

    public record Assignment(@NotNull @Min(1) Long productId, @Min(-10000) @Max(10000) int sortOrder,
                             Boolean tourRoute) {
        public Assignment(Long productId, int sortOrder) { this(productId,sortOrder,false); }
    }

    @PostMapping("/{zone}/products")
    @Transactional(rollbackFor=Exception.class)
    public CommonResult<Void> assign(Principal principal, @PathVariable String zone,
            @Valid @RequestBody Assignment body) {
        long operator = access.requireAuthorized(principal);
        validateZone(zone);
        // Product row lock serializes retries and concurrent zone edits for the same product.
        List<Long> products = jdbc.queryForList("SELECT id FROM pms_product WHERE id=? AND delete_status=0 FOR UPDATE",Long.class,body.productId());
        if (products.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"商品不存在");
        if ("PICK".equals(zone)) {
            if (!Boolean.TRUE.equals(body.tourRoute()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请确认该商品类型为旅游路线");
            Integer routes = jdbc.queryForObject("SELECT COUNT(*) FROM pms_product p "
                    + "JOIN pms_product_category c ON c.id=p.product_category_id "
                    + "WHERE p.id=? AND c.name='旅游路线'",Integer.class,body.productId());
            BigDecimal price = jdbc.queryForObject("SELECT price FROM pms_product WHERE id=?",BigDecimal.class,body.productId());
            Integer invalidSkus = jdbc.queryForObject("SELECT COUNT(*) FROM pms_sku_stock WHERE product_id=? AND (price IS NULL OR price<>3999.00)",Integer.class,body.productId());
            Integer skuCount = jdbc.queryForObject("SELECT COUNT(*) FROM pms_sku_stock WHERE product_id=?",Integer.class,body.productId());
            if (routes == null || routes != 1 || price == null || price.compareTo(new BigDecimal("3999.00")) != 0
                    || invalidSkus == null || invalidSkus > 0 || skuCount == null || skuCount == 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"自选区须选择旅游路线分类，商品及所有规格统一售价3999元");
            jdbc.update("INSERT INTO tri_membership_product(product_id,enabled,updated_by) VALUES(?,1,?)"
                    + " ON DUPLICATE KEY UPDATE enabled=1,updated_by=VALUES(updated_by)",body.productId(),operator);
        } else {
            jdbc.update("UPDATE tri_membership_product SET enabled=0 WHERE product_id=?",body.productId());
        }
        jdbc.update("DELETE FROM pms_product_zone WHERE product_id=? AND zone_code<>?",body.productId(),zone);
        List<Integer> old = jdbc.queryForList("SELECT sort_order FROM pms_product_zone WHERE product_id=? AND zone_code=?",Integer.class,body.productId(),zone);
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

