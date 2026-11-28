package com.macro.mall.common.service;

import com.macro.mall.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.*;

/** 消费区三级配置：主题(海南旅游) -> 目的地(三亚) -> 旅游线路商品。商品价格、库存与结算仍由原商品系统负责。 */
@Service
public class ConsumeRouteService {
    private final JdbcTemplate jdbc;

    public ConsumeRouteService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Theme(Long id, String name, String imageUrl, String description, boolean enabled, int sortOrder) {}
    public record Destination(Long id, Long themeId, String name, String imageUrl, String description,
                              boolean enabled, int sortOrder) {}
    public record Route(Long id, Long destinationId, String name, String imageUrl, String description,
                        Long productId, boolean enabled, int sortOrder) {}

    private static final String THEME_FIELDS = "id,name,image_url AS imageUrl,description,enabled,sort_order AS sortOrder";
    private static final String DEST_FIELDS = "id,theme_id AS themeId,name,image_url AS imageUrl,description,enabled,sort_order AS sortOrder";
    private static final String ROUTE_FIELDS = "r.id,r.destination_id AS destinationId,r.name,r.image_url AS imageUrl,"
            + "r.description,r.product_id AS productId,r.enabled,r.sort_order AS sortOrder,"
            + "p.name AS productName,p.pic AS productPic,p.sub_title AS productSubTitle,p.price";

    /** 商城端只看启用项；管理端看全部。 */
    public List<Map<String, Object>> listThemes(boolean publicView) {
        return jdbc.queryForList("SELECT " + THEME_FIELDS + " FROM pms_consume_theme"
                + (publicView ? " WHERE enabled=1" : "") + " ORDER BY sort_order DESC,id ASC");
    }

    public List<Map<String, Object>> listDestinations(boolean publicView, Long themeId) {
        StringBuilder sql = new StringBuilder("SELECT " + DEST_FIELDS + " FROM pms_consume_destination WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (publicView) sql.append(" AND enabled=1");
        if (themeId != null && themeId > 0) { sql.append(" AND theme_id=?"); params.add(themeId); }
        sql.append(" ORDER BY sort_order DESC,id ASC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoutes(boolean publicView, Long destinationId) {
        StringBuilder sql = new StringBuilder("SELECT " + ROUTE_FIELDS + " FROM pms_consume_route r "
                + "JOIN pms_product p ON p.id=r.product_id AND p.delete_status=0 WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (publicView) sql.append(" AND r.enabled=1 AND p.publish_status=1");
        if (destinationId != null && destinationId > 0) {
            sql.append(" AND r.destination_id=?");
            params.add(destinationId);
        }
        sql.append(" ORDER BY r.sort_order DESC,r.id ASC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> candidates(String keyword) {
        if (keyword != null && keyword.length() > 80) throw new ApiException("搜索词不能超过80字");
        String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbc.queryForList("SELECT p.id,p.name,p.pic,p.price,p.publish_status AS publishStatus FROM pms_product p "
                + "WHERE p.delete_status=0 AND p.name LIKE ? ORDER BY p.id DESC LIMIT 100", pattern);
    }

    @Transactional
    public long saveTheme(Theme t) {
        validateTheme(t);
        if (t.id() == null) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var st = connection.prepareStatement("INSERT INTO pms_consume_theme(name,image_url,description,enabled,sort_order) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                st.setString(1, t.name().trim());
                st.setString(2, t.imageUrl() == null ? "" : t.imageUrl().trim());
                st.setString(3, t.description() == null ? "" : t.description().trim());
                st.setInt(4, t.enabled() ? 1 : 0);
                st.setInt(5, t.sortOrder());
                return st;
            }, key);
            return Objects.requireNonNull(key.getKey()).longValue();
        }
        if (jdbc.update("UPDATE pms_consume_theme SET name=?,image_url=?,description=?,enabled=?,sort_order=? WHERE id=?",
                t.name().trim(), t.imageUrl() == null ? "" : t.imageUrl().trim(),
                t.description() == null ? "" : t.description().trim(), t.enabled() ? 1 : 0, t.sortOrder(), t.id()) == 0) {
            throw new ApiException("主题不存在");
        }
        return t.id();
    }

    @Transactional
    public void deleteTheme(long id) {
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM pms_consume_destination WHERE theme_id=?", Integer.class, id);
        if (used != null && used > 0) throw new ApiException("该主题仍有目的地，请先移除或改归属");
        if (jdbc.update("DELETE FROM pms_consume_theme WHERE id=?", id) == 0) throw new ApiException("主题不存在");
    }

    @Transactional
    public long saveDestination(Destination d) {
        validateDest(d);
        Integer themeCount = jdbc.queryForObject("SELECT COUNT(*) FROM pms_consume_theme WHERE id=?", Integer.class, d.themeId());
        if (themeCount == null || themeCount == 0) throw new ApiException("主题不存在");
        if (d.id() == null) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var st = connection.prepareStatement("INSERT INTO pms_consume_destination(theme_id,name,image_url,description,enabled,sort_order) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                st.setLong(1, d.themeId());
                st.setString(2, d.name().trim());
                st.setString(3, d.imageUrl() == null ? "" : d.imageUrl().trim());
                st.setString(4, d.description() == null ? "" : d.description().trim());
                st.setInt(5, d.enabled() ? 1 : 0);
                st.setInt(6, d.sortOrder());
                return st;
            }, key);
            return Objects.requireNonNull(key.getKey()).longValue();
        }
        if (jdbc.update("UPDATE pms_consume_destination SET theme_id=?,name=?,image_url=?,description=?,enabled=?,sort_order=? WHERE id=?",
                d.themeId(), d.name().trim(), d.imageUrl() == null ? "" : d.imageUrl().trim(),
                d.description() == null ? "" : d.description().trim(), d.enabled() ? 1 : 0, d.sortOrder(), d.id()) == 0) {
            throw new ApiException("目的地不存在");
        }
        return d.id();
    }

    @Transactional
    public void deleteDestination(long id) {
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM pms_consume_route WHERE destination_id=?", Integer.class, id);
        if (used != null && used > 0) throw new ApiException("该目的地仍有线路，请先移除或改归属");
        if (jdbc.update("DELETE FROM pms_consume_destination WHERE id=?", id) == 0) throw new ApiException("目的地不存在");
    }

    @Transactional
    public long saveRoute(Route r) {
        validateRoute(r);
        var product = jdbc.queryForList("SELECT id FROM pms_product WHERE id=? AND delete_status=0", r.productId());
        if (product.isEmpty()) throw new ApiException("关联商品不存在或已删除");
        Integer destCount = jdbc.queryForObject("SELECT COUNT(*) FROM pms_consume_destination WHERE id=?", Integer.class, r.destinationId());
        if (destCount == null || destCount == 0) throw new ApiException("目的地不存在");
        if (r.id() == null) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var st = connection.prepareStatement("INSERT INTO pms_consume_route(destination_id,name,image_url,description,product_id,enabled,sort_order) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                st.setLong(1, r.destinationId());
                st.setString(2, r.name().trim());
                st.setString(3, r.imageUrl() == null ? "" : r.imageUrl().trim());
                st.setString(4, r.description() == null ? "" : r.description().trim());
                st.setLong(5, r.productId());
                st.setInt(6, r.enabled() ? 1 : 0);
                st.setInt(7, r.sortOrder());
                return st;
            }, key);
            return Objects.requireNonNull(key.getKey()).longValue();
        }
        if (jdbc.update("UPDATE pms_consume_route SET destination_id=?,name=?,image_url=?,description=?,product_id=?,enabled=?,sort_order=? WHERE id=?",
                r.destinationId(), r.name().trim(), r.imageUrl() == null ? "" : r.imageUrl().trim(),
                r.description() == null ? "" : r.description().trim(), r.productId(), r.enabled() ? 1 : 0, r.sortOrder(), r.id()) == 0) {
            throw new ApiException("线路不存在");
        }
        return r.id();
    }

    @Transactional
    public void deleteRoute(long id) {
        if (jdbc.update("DELETE FROM pms_consume_route WHERE id=?", id) == 0) throw new ApiException("线路不存在");
    }

    private static void validateTheme(Theme t) {
        if (t == null || t.name() == null || t.name().isBlank() || t.name().length() > 120
                || (t.imageUrl() != null && (t.imageUrl().length() > 500 || !safeImage(t.imageUrl())))
                || (t.description() != null && t.description().length() > 500)
                || (t.id() != null && t.id() <= 0)) {
            throw new ApiException("请填写主题名称（≤120字），封面地址合法，简介≤500字");
        }
    }
    private static void validateDest(Destination d) {
        if (d == null || d.themeId() == null || d.themeId() <= 0
                || d.name() == null || d.name().isBlank() || d.name().length() > 120
                || (d.imageUrl() != null && (d.imageUrl().length() > 500 || !safeImage(d.imageUrl())))
                || (d.description() != null && d.description().length() > 500)
                || (d.id() != null && d.id() <= 0)) {
            throw new ApiException("请填写目的地名称（≤120字）、选择归属主题，封面地址合法，简介≤500字");
        }
    }

    private static void validateRoute(Route r) {
        if (r == null || r.destinationId() == null || r.destinationId() <= 0
                || r.productId() == null || r.productId() <= 0
                || (r.id() != null && r.id() <= 0)) {
            throw new ApiException("请选择归属目的地与关联商品");
        }
    }

    private static boolean safeImage(String value) {
        if (value.isEmpty()) return true;
        boolean prefix = value.startsWith("/product-images/") || value.startsWith("/oss/") || value.startsWith("/static/");
        return prefix && !value.contains("..") && !value.contains("\\") && !value.contains("\n") && !value.contains("\r");
    }
}
