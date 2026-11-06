package com.macro.mall.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ProductZoneBatchService {
    private final JdbcTemplate jdbc;
    private final ProductTrashService trash;
    private final PmsProductService products;

    public ProductZoneBatchService(JdbcTemplate jdbc, ProductTrashService trash, PmsProductService products) {
        this.jdbc = jdbc;
        this.trash = trash;
        this.products = products;
    }

    @Transactional
    public int trash(String zone, List<Long> ids, long operator, String name) {
        List<Long> targets = lockSelection(zone, ids);
        for (long id : targets) trash.trash(zone, id, operator, name);
        return targets.size();
    }

    @Transactional
    public int publish(String zone, List<Long> ids, long operator, String name) {
        List<Long> targets = lockSelection(zone, ids);
        int count = products.updatePublishStatus(targets, 1);
        for (long id : targets) {
            jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) VALUES(?,?,?,?,?,?)",
                    operator, name, "product_zone", "batch_publish", id, zone);
        }
        return count;
    }

    private List<Long> lockSelection(String zone, List<Long> ids) {
        trash.zone(zone);
        if (ids == null || ids.isEmpty() || ids.size() > 10000
                || ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择1至10000个当前专区商品");
        }
        List<Long> targets = ids.stream().distinct().sorted().toList();
        // Product row locks match zone reassignment and trash restoration, in stable ID order.
        for (long id : targets) {
            var found = jdbc.queryForList("SELECT id FROM pms_product WHERE id=? AND delete_status=0 FOR UPDATE", Long.class, id);
            if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "商品" + id + "已变化，请刷新后重新选择");
            var zones = jdbc.queryForList("SELECT zone_code FROM pms_product_zone WHERE product_id=? ORDER BY zone_code FOR UPDATE", String.class, id);
            if (zones.size() != 1 || !zone.equals(zones.get(0))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "商品" + id + "不单独属于当前专区，整批操作已取消");
            }
        }
        return targets;
    }
}
