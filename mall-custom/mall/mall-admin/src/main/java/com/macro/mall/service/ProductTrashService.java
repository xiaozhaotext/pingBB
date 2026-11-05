package com.macro.mall.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class ProductTrashService {
    private final JdbcTemplate jdbc;
    public ProductTrashService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void zone(String zone) {
        if (!Set.of("PICK","REBUY","CONSUME").contains(zone)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"专区无效");
    }
    public int days(String zone) {
        zone(zone);
        return jdbc.queryForObject("SELECT retention_days FROM pms_trash_policy WHERE zone_code=?",Integer.class,zone);
    }
    @Transactional
    public void setDays(String zone,int days,long operator,String name) {
        zone(zone);
        if(days<1 || days>3650) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"保留天数须在1至3650之间");
        jdbc.update("UPDATE pms_trash_policy SET retention_days=? WHERE zone_code=?",days,zone);
        audit(operator,name,"retention",0,zone+" days="+days);
    }
    public List<Map<String,Object>> list(String zone,String keyword) {
        zone(zone);
        if(keyword.length()>120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"搜索词过长");
        return jdbc.queryForList("SELECT p.id,p.name,p.pic,p.product_sn AS productSn,p.price,t.deleted_at AS deletedAt FROM pms_product_trash t JOIN pms_product p ON p.id=t.product_id WHERE t.zone_code=? AND p.delete_status=1 AND (p.name LIKE ? OR p.product_sn LIKE ?) ORDER BY t.deleted_at DESC,p.id DESC",zone,"%"+keyword.trim()+"%","%"+keyword.trim()+"%");
    }
    @Transactional
    public void trash(String zone,long id,long operator,String name) {
        zone(zone);
        var found=jdbc.queryForList("SELECT p.id FROM pms_product p JOIN pms_product_zone z ON z.product_id=p.id WHERE p.id=? AND z.zone_code=? AND p.delete_status=0 FOR UPDATE",Long.class,id,zone);
        if(found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"商品不属于当前专区或已删除");
        jdbc.update("INSERT INTO pms_product_trash(product_id,zone_code,deleted_by) VALUES(?,?,?)",id,zone,operator);
        jdbc.update("UPDATE pms_product SET delete_status=1,publish_status=0 WHERE id=?",id);
        audit(operator,name,"trash",id,zone);
    }
    @Transactional
    public void restore(String zone,long id,long operator,String name) {
        lock(zone,id);
        jdbc.update("UPDATE pms_product SET delete_status=0,publish_status=0 WHERE id=?",id);
        jdbc.update("DELETE FROM pms_product_trash WHERE product_id=?",id);
        audit(operator,name,"restore",id,zone+" restored unpublished");
    }
    private void lock(String zone,long id) {
        zone(zone);
        // Same lock order as assignment/deletion prevents restore/purge races.
        jdbc.queryForList("SELECT id FROM pms_product WHERE id=? FOR UPDATE",Long.class,id);
        var rows=jdbc.queryForList("SELECT t.product_id FROM pms_product_trash t JOIN pms_product p ON p.id=t.product_id WHERE t.product_id=? AND t.zone_code=? AND p.delete_status=1 FOR UPDATE",Long.class,id,zone);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"回收站商品不存在或不属于当前专区");
    }
    @Transactional
    public int purge(String zone,List<Long> ids,boolean all,long operator,String name) {
        zone(zone);
        List<Long> targets;
        if(all) targets=jdbc.queryForList("SELECT product_id FROM pms_product_trash WHERE zone_code=? ORDER BY product_id",Long.class,zone);
        else {
            if(ids==null || ids.isEmpty() || ids.size()>1000 || ids.stream().anyMatch(id->id==null || id<1)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择1至1000个商品");
            targets=ids.stream().distinct().sorted().toList();
        }
        // Validate entire selection before deleting anything; a foreign zone cannot be smuggled into a batch.
        for(long id:targets) lock(zone,id);
        for(long id:targets) erase(zone,id,operator,name);
        return targets.size();
    }
    private void erase(String zone,long id,long operator,String name) {
        // Order/financial snapshots remain intact. Only mutable catalog references are removed.
        jdbc.update("DELETE FROM pms_comment_replay WHERE comment_id IN (SELECT id FROM pms_comment WHERE product_id=?)",id);
        for(String table:List.of("pms_comment","pms_member_price","pms_product_attribute_value","pms_product_full_reduction","pms_product_ladder","pms_product_operate_log","pms_product_vertify_record","pms_sku_stock","pms_zone_test_product","tri_membership_product","sms_coupon_product_relation","sms_flash_promotion_product_relation","sms_home_new_product","sms_home_recommend_product","cms_prefrence_area_product_relation","cms_subject_product_relation","oms_cart_item","ums_member_product_collection","ums_member_read_history"))
            jdbc.update("DELETE FROM "+table+" WHERE product_id=?",id);
        jdbc.update("DELETE FROM pms_product_pic_bak WHERE id=?",id);
        jdbc.update("DELETE FROM pms_product_zone WHERE product_id=?",id);
        jdbc.update("DELETE FROM pms_product_trash WHERE product_id=?",id);
        jdbc.update("DELETE FROM pms_product WHERE id=? AND delete_status=1",id);
        audit(operator,name,"purge",id,zone);
    }
    @Scheduled(cron="${mall.product-trash.cleanup-cron:0 15 * * * *}",zone="Asia/Shanghai")
    @Transactional
    public void cleanupExpired() {
        for(String zone:List.of("PICK","REBUY","CONSUME")) {
            int days=days(zone);
            var ids=jdbc.queryForList("SELECT product_id FROM pms_product_trash WHERE zone_code=? AND deleted_at<=TIMESTAMPADD(DAY,?,CURRENT_TIMESTAMP) ORDER BY product_id LIMIT 500",Long.class,zone,-days);
            for(long id:ids) {
                try { lock(zone,id); } catch (ResponseStatusException ex) { if(ex.getStatusCode()==HttpStatus.NOT_FOUND) continue; throw ex; }
                // Recheck expiry after acquiring locks, including a policy changed concurrently.
                int expired=jdbc.queryForObject("SELECT COUNT(*) FROM pms_product_trash t JOIN pms_trash_policy c ON c.zone_code=t.zone_code WHERE t.product_id=? AND t.deleted_at<=TIMESTAMPADD(DAY,-c.retention_days,CURRENT_TIMESTAMP)",Integer.class,id);
                if(expired>0) erase(zone,id,0,"automatic-cleanup");
            }
        }
    }
    private void audit(long id,String name,String action,long target,String detail) {
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) VALUES(?,?,?,?,?,?)",id,name,"product_trash",action,target,detail);
    }
}
