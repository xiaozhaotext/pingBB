package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** 自选/复购/消费专区商品列表：仅展示明确分配的在售商品，结算规则由各专区独立决定。 */
@RestController
@RequestMapping("/product/zone")
public class ProductZoneController {
    private final JdbcTemplate jdbcTemplate;

    public ProductZoneController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{zone}")
    public CommonResult<List<Map<String, Object>>> list(@PathVariable String zone,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "latest") String sort) {
        if (!"PICK".equals(zone) && !"REBUY".equals(zone) && !"CONSUME".equals(zone)) {
            throw new ApiException("未知商品专区：" + zone);
        }
        if (keyword != null && keyword.length() > 80) {
            throw new ApiException("搜索词过长");
        }
        StringBuilder sql = new StringBuilder(
                "SELECT p.id,p.name,p.pic,p.sub_title AS subTitle,p.price,p.sale,p.product_category_id AS productCategoryId "
                        + "FROM pms_product_zone z JOIN pms_product p ON p.id=z.product_id "
                        + "WHERE z.zone_code=? AND p.publish_status=1 AND p.delete_status=0 "
                        + "AND (p.brand_id IS NULL OR p.brand_id=0 OR NOT EXISTS "
                        + "(SELECT 1 FROM pms_brand b WHERE b.id=p.brand_id AND b.show_status<>1))");
        List<Object> params = new ArrayList<>();
        params.add(zone);
        if ("PICK".equals(zone)) {
            sql.append(" AND p.price=3999.00"
                    + " AND EXISTS (SELECT 1 FROM tri_membership_product t"
                    + " WHERE t.product_id=p.id AND t.enabled=1)"
                    + " AND EXISTS (SELECT 1 FROM pms_sku_stock s"
                    + " WHERE s.product_id=p.id AND s.price=3999.00)"
                    + " AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s"
                    + " WHERE s.product_id=p.id AND (s.price IS NULL OR s.price<>3999.00))");
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR p.keywords LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (categoryId != null && categoryId > 0) {
            sql.append(" AND p.product_category_id=?");
            params.add(categoryId);
        }
        switch (sort) {
            case "sale" -> sql.append(" ORDER BY p.sale DESC,p.id DESC");
            case "price_asc" -> sql.append(" ORDER BY p.price ASC,p.id DESC");
            case "price_desc" -> sql.append(" ORDER BY p.price DESC,p.id DESC");
            default -> sql.append(" ORDER BY z.sort_order DESC,p.id DESC");
        }
        sql.append(" LIMIT 100");
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
                sql.toString(), params.toArray());
        return CommonResult.success(products);
    }
}
