package com.macro.mall.service;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductTrashServiceTest {
    JdbcTemplate jdbc; ProductTrashService service; TransactionTemplate tx;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));service=new ProductTrashService(jdbc);
        jdbc.execute("CREATE TABLE pms_product(id BIGINT PRIMARY KEY,name VARCHAR(100),pic VARCHAR(100),product_sn VARCHAR(100),price DECIMAL(10,2),delete_status INT,publish_status INT)");
        jdbc.execute("CREATE TABLE pms_product_zone(product_id BIGINT PRIMARY KEY,zone_code VARCHAR(16))");
        jdbc.execute("CREATE TABLE pms_product_trash(product_id BIGINT PRIMARY KEY,zone_code VARCHAR(16),deleted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted_by BIGINT)");
        jdbc.execute("CREATE TABLE pms_trash_policy(zone_code VARCHAR(16) PRIMARY KEY,retention_days INT)");
        jdbc.execute("CREATE TABLE sys_audit_log(operator_id BIGINT,operator_name VARCHAR(100),module VARCHAR(100),action VARCHAR(100),target_id BIGINT,detail VARCHAR(255))");
        jdbc.execute("CREATE TABLE pms_comment(id BIGINT,product_id BIGINT)");
        jdbc.execute("CREATE TABLE pms_comment_replay(comment_id BIGINT)");
        jdbc.execute("CREATE TABLE pms_product_pic_bak(id BIGINT)");
        jdbc.execute("CREATE TABLE oms_order_item(product_id BIGINT,product_name VARCHAR(100))");
        for(String t:List.of("pms_member_price","pms_product_attribute_value","pms_product_full_reduction","pms_product_ladder","pms_product_operate_log","pms_product_vertify_record","pms_sku_stock","pms_zone_test_product","tri_membership_product","sms_coupon_product_relation","sms_flash_promotion_product_relation","sms_home_new_product","sms_home_recommend_product","cms_prefrence_area_product_relation","cms_subject_product_relation","oms_cart_item","ums_member_product_collection","ums_member_read_history")) jdbc.execute("CREATE TABLE "+t+"(product_id BIGINT)");
        for(String z:List.of("PICK","REBUY","CONSUME")) jdbc.update("INSERT INTO pms_trash_policy VALUES(?,30)",z);
        jdbc.update("INSERT INTO pms_product VALUES(1,'one','','sn',3999,0,1),(2,'two','','sn2',100,0,1)");
        jdbc.update("INSERT INTO pms_product_zone VALUES(1,'PICK'),(2,'REBUY')");
    }
    @Test void publishedDeletionMovesToRecycleBinAndKeepsOrderSnapshot() {
        jdbc.update("INSERT INTO oms_order_item VALUES(1,'historical name')");
        tx.executeWithoutResult(s -> service.trash("PICK",1,5,"admin"));
        assertEquals(0,jdbc.queryForObject("SELECT publish_status FROM pms_product WHERE id=1",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT delete_status FROM pms_product WHERE id=1",Integer.class));
        assertEquals(1,service.list("PICK","").size());
        assertEquals("historical name",jdbc.queryForObject("SELECT product_name FROM oms_order_item WHERE product_id=1",String.class));
        tx.executeWithoutResult(s -> service.restore("PICK",1,5,"admin"));
        assertTrue(service.list("PICK","").isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT publish_status FROM pms_product WHERE id=1",Integer.class));
    }
    @Test void deletionAndRestoreRemainScopedAndUnpublished() {
        assertThrows(ResponseStatusException.class,()->service.trash("REBUY",1,5,"admin"));
        tx.executeWithoutResult(s->service.trash("PICK",1,5,"admin"));
        assertEquals(1,service.list("PICK","").size());assertTrue(service.list("REBUY","").isEmpty());
        assertThrows(ResponseStatusException.class,()->service.restore("REBUY",1,5,"admin"));
        tx.executeWithoutResult(s->service.restore("PICK",1,5,"admin"));
        assertEquals(0,jdbc.queryForObject("SELECT publish_status FROM pms_product WHERE id=1",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT delete_status FROM pms_product WHERE id=1",Integer.class));
        assertTrue(service.list("PICK","").isEmpty());
    }
    @Test void mixedZoneBatchFailsWithoutPartialDeletion() {
        tx.executeWithoutResult(s->{service.trash("PICK",1,5,"admin");service.trash("REBUY",2,5,"admin");});
        assertThrows(ResponseStatusException.class,()->tx.executeWithoutResult(s->service.purge("PICK",List.of(1L,2L),false,5,"admin")));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM pms_product",Integer.class));
    }
    @Test void permanentDeletionRemovesCatalogButPreservesOrderSnapshot() {
        jdbc.update("INSERT INTO pms_sku_stock VALUES(1)");jdbc.update("INSERT INTO oms_order_item VALUES(1,'historical name')");
        tx.executeWithoutResult(s->service.trash("PICK",1,5,"admin"));
        assertEquals(Integer.valueOf(1),tx.execute(s->service.purge("PICK",List.of(),true,5,"admin")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pms_sku_stock",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oms_order_item",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pms_product",Integer.class));
    }
    @Test void automaticCleanupHonorsEachZoneRetention() {
        tx.executeWithoutResult(s->{service.trash("PICK",1,5,"admin");service.trash("REBUY",2,5,"admin");});
        jdbc.update("UPDATE pms_product_trash SET deleted_at=TIMESTAMPADD(DAY,-31,CURRENT_TIMESTAMP)");
        service.setDays("REBUY",60,5,"admin");
        tx.executeWithoutResult(s->service.cleanupExpired());
        assertTrue(service.list("PICK","").isEmpty());assertEquals(1,service.list("REBUY","").size());
        assertThrows(ResponseStatusException.class,()->service.setDays("PICK",0,5,"admin"));
        assertThrows(ResponseStatusException.class,()->service.setDays("PICK",3651,5,"admin"));
    }
}
