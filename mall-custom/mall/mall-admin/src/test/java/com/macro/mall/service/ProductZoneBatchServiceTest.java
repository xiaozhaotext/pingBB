package com.macro.mall.service;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductZoneBatchServiceTest {
    JdbcTemplate jdbc; ProductZoneBatchService service; TransactionTemplate tx;
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE pms_product(id BIGINT PRIMARY KEY,delete_status INT,publish_status INT)");
        jdbc.execute("CREATE TABLE pms_product_zone(product_id BIGINT,zone_code VARCHAR(16),PRIMARY KEY(product_id,zone_code))");
        jdbc.execute("CREATE TABLE pms_product_trash(product_id BIGINT PRIMARY KEY,zone_code VARCHAR(16),deleted_by BIGINT)");
        jdbc.execute("CREATE TABLE sys_audit_log(operator_id BIGINT,operator_name VARCHAR(100),module VARCHAR(100),action VARCHAR(100),target_id BIGINT,detail VARCHAR(255))");
        jdbc.update("INSERT INTO pms_product VALUES(1,0,0),(2,0,0),(3,0,0)");
        jdbc.update("INSERT INTO pms_product_zone VALUES(1,'REBUY'),(2,'CONSUME'),(3,'REBUY')");
        var products = mock(PmsProductService.class);
        when(products.updatePublishStatus(anyList(),eq(1))).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            for (long id : ids) jdbc.update("UPDATE pms_product SET publish_status=1 WHERE id=?",id);
            return ids.size();
        });
        service = new ProductZoneBatchService(jdbc,new ProductTrashService(jdbc),products);
    }
    int value(String column,long id) { return jdbc.queryForObject("SELECT "+column+" FROM pms_product WHERE id=?",Integer.class,id); }
    @Test void publishesOnlySelectedCurrentZoneAndDeduplicates() {
        assertEquals(Integer.valueOf(2),tx.execute(s -> service.publish("REBUY",List.of(3L,1L,1L),5,"admin")));
        assertEquals(1,value("publish_status",1));
        assertEquals(1,value("publish_status",3));
        assertEquals(0,value("publish_status",2));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class));
    }
    @Test void trashUsesRecycleBinAndPreservesOtherZone() {
        tx.executeWithoutResult(s -> service.trash("REBUY",List.of(1L,3L),5,"admin"));
        assertEquals(1,value("delete_status",1));
        assertEquals(0,value("delete_status",2));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM pms_product_trash WHERE zone_code='REBUY'",Integer.class));
    }
    @Test void mixedZoneRejectsBeforeAnyMutation() {
        assertThrows(ResponseStatusException.class,() -> tx.executeWithoutResult(s -> service.trash("REBUY",List.of(1L,2L),5,"admin")));
        assertThrows(ResponseStatusException.class,() -> tx.executeWithoutResult(s -> service.publish("REBUY",List.of(1L,2L),5,"admin")));
        assertEquals(0,value("delete_status",1));
        assertEquals(0,value("publish_status",1));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class));
    }
    @Test void ambiguousLegacyZoneAndInvalidSelectionsReject() {
        jdbc.update("INSERT INTO pms_product_zone VALUES(3,'CONSUME')");
        assertThrows(ResponseStatusException.class,() -> tx.executeWithoutResult(s -> service.publish("REBUY",List.of(1L,3L),5,"admin")));
        assertThrows(ResponseStatusException.class,() -> service.publish("REBUY",List.of(),5,"admin"));
        assertThrows(ResponseStatusException.class,() -> service.trash("REBUY",List.of(-1L),5,"admin"));
        assertEquals(0,value("publish_status",1));
    }
    @Test void auditFailureRollsBackEntirePublishAndTrash() {
        jdbc.execute("ALTER TABLE sys_audit_log ADD CONSTRAINT reject_three CHECK(target_id<>3)");
        assertThrows(Exception.class,() -> tx.executeWithoutResult(s -> service.publish("REBUY",List.of(1L,3L),5,"admin")));
        assertEquals(0,value("publish_status",1));
        assertThrows(Exception.class,() -> tx.executeWithoutResult(s -> service.trash("REBUY",List.of(1L,3L),5,"admin")));
        assertEquals(0,value("delete_status",1));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pms_product_trash",Integer.class));
    }
}

