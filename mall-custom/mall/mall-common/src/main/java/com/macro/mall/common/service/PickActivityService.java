package com.macro.mall.common.service;

import com.macro.mall.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.*;

/** 自选区旅游活动配置，商品价格、库存和结算仍由原商品系统负责。 */
@Service
public class PickActivityService {
    private final JdbcTemplate jdbc;

    public PickActivityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Activity(Long id, String title, String imageUrl, String rules,
                           boolean enabled, int sortOrder, List<Long> productIds) {}

    private static final String FIELDS = "id,title,image_url AS imageUrl,rules,enabled,sort_order AS sortOrder";
    private static final String ELIGIBLE = " z.zone_code='PICK' AND p.delete_status=0 AND p.publish_status=1"
            + " AND p.price=3999.00"
            + " AND (p.brand_id IS NULL OR p.brand_id=0 OR NOT EXISTS"
            + " (SELECT 1 FROM pms_brand b WHERE b.id=p.brand_id AND b.show_status<>1))"
            + " AND EXISTS (SELECT 1 FROM tri_membership_product t WHERE t.product_id=p.id AND t.enabled=1)"
            + " AND EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price=3999.00)"
            + " AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND (s.price IS NULL OR s.price<>3999.00))";

    public List<Map<String, Object>> list(boolean publicView) {
        return jdbc.queryForList("SELECT " + FIELDS + " FROM pms_pick_activity"
                + (publicView ? " WHERE enabled=1" : "") + " ORDER BY sort_order DESC,id ASC");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(long id, boolean publicView) {
        var rows = jdbc.queryForList("SELECT " + FIELDS + " FROM pms_pick_activity WHERE id=?"
                + (publicView ? " AND enabled=1" : ""), id);
        if (rows.isEmpty()) throw new ApiException("活动不存在或已停用");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        var products = jdbc.queryForList("SELECT p.id,p.name,p.pic,p.sub_title AS subTitle,p.price,p.publish_status AS publishStatus "
                + "FROM pms_pick_activity_product ap JOIN pms_product p ON p.id=ap.product_id "
                + "LEFT JOIN pms_product_zone z ON z.product_id=p.id WHERE ap.activity_id=?"
                + (publicView ? " AND" + ELIGIBLE : "") + " ORDER BY ap.sort_order ASC,p.id ASC", id);
        result.put("products", products);
        return result;
    }

    public List<Map<String, Object>> candidates(String keyword) {
        if (keyword != null && keyword.length() > 80) throw new ApiException("搜索词不能超过80字");
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbc.queryForList("SELECT p.id,p.name,p.pic,p.price,p.publish_status AS publishStatus FROM pms_product p "
                + "JOIN pms_product_zone z ON z.product_id=p.id WHERE z.zone_code='PICK' AND p.delete_status=0 "
                + "AND p.name LIKE ? ORDER BY p.id DESC LIMIT 100", pattern);
    }

    @Transactional
    public long save(Activity activity) {
        validate(activity);
        Long id = activity.id();
        if (id != null) {
            var existing = jdbc.queryForList("SELECT id FROM pms_pick_activity WHERE id=? FOR UPDATE", id);
            if (existing.isEmpty()) throw new ApiException("活动不存在，请刷新列表");
        }
        // 锁定商品及专区归属，防止编辑过程中把非自选区商品关联到活动。
        for (Long productId : activity.productIds()) {
            var products = jdbc.queryForList("SELECT p.id FROM pms_product p JOIN pms_product_zone z ON z.product_id=p.id "
                    + "WHERE p.id=? AND p.delete_status=0 AND z.zone_code='PICK' FOR UPDATE", productId);
            if (products.isEmpty()) throw new ApiException("所选商品已删除或不属于自选区，请重新选择");
        }
        if (id == null) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO pms_pick_activity(title,image_url,rules,enabled,sort_order) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, activity.title().trim());
                statement.setString(2, activity.imageUrl().trim());
                statement.setString(3, activity.rules().trim());
                statement.setInt(4, activity.enabled() ? 1 : 0);
                statement.setInt(5, activity.sortOrder());
                return statement;
            }, key);
            id = Objects.requireNonNull(key.getKey()).longValue();
        } else {
            jdbc.update("UPDATE pms_pick_activity SET title=?,image_url=?,rules=?,enabled=?,sort_order=? WHERE id=?",
                    activity.title().trim(), activity.imageUrl().trim(), activity.rules().trim(), activity.enabled() ? 1 : 0, activity.sortOrder(), id);
            jdbc.update("DELETE FROM pms_pick_activity_product WHERE activity_id=?", id);
        }
        for (int index = 0; index < activity.productIds().size(); index++) {
            jdbc.update("INSERT INTO pms_pick_activity_product(activity_id,product_id,sort_order) VALUES(?,?,?)", id, activity.productIds().get(index), index);
        }
        return id;
    }

    private static void validate(Activity a) {
        if (a == null || a.title() == null || a.title().isBlank() || a.title().length() > 120
                || a.rules() == null || a.rules().isBlank() || a.rules().length() > 20000
                || a.imageUrl() == null || a.imageUrl().length() > 500 || !safeImage(a.imageUrl())
                || a.productIds() == null || a.productIds().isEmpty() || a.productIds().size() > 50
                || a.productIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(a.productIds()).size() != a.productIds().size()
                || (a.id() != null && a.id() <= 0)) {
            throw new ApiException("请填写活动名称、上传图片、填写规则，并选择1至50件不重复的自选区正品");
        }
    }

    private static boolean safeImage(String value) {
        return (value.startsWith("/product-images/") || value.startsWith("/oss/"))
                && !value.contains("..") && !value.contains("\\") && !value.contains("\n") && !value.contains("\r");
    }
}
