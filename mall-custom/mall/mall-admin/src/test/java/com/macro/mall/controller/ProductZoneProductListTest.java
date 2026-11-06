package com.macro.mall.controller;

import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductZoneProductListTest {
    @Test void returnsBeyondHundredUpToTenThousandWhilePreservingZoneAndSearch() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE pms_product(id BIGINT PRIMARY KEY,name VARCHAR(100),product_sn VARCHAR(100),pic VARCHAR(100),price DECIMAL(10,2),publish_status INT,delete_status INT,brand_id BIGINT)");
        jdbc.execute("CREATE TABLE pms_product_zone(product_id BIGINT PRIMARY KEY,zone_code VARCHAR(16),sort_order INT)");
        jdbc.execute("CREATE TABLE tri_membership_product(product_id BIGINT,enabled INT)");
        jdbc.execute("CREATE TABLE pms_brand(id BIGINT,show_status INT)");
        jdbc.execute("INSERT INTO pms_product SELECT X,CONCAT('product-',X),CONCAT('sn-',X),'',10,0,0,NULL FROM SYSTEM_RANGE(1,10003)");
        jdbc.execute("INSERT INTO pms_product_zone SELECT X,CASE WHEN X=10002 THEN 'CONSUME' ELSE 'REBUY' END,0 FROM SYSTEM_RANGE(1,10003)");
        jdbc.update("UPDATE pms_product SET delete_status=1 WHERE id=10003");
        var controller = new ProductZoneAdminController(jdbc,mock(P0AdminAccess.class));
        var rows = controller.products(()->"admin","REBUY","",true).getData();
        assertEquals(10000,rows.size());
        assertEquals(10001L,((Number)rows.get(0).get("id")).longValue());
        assertTrue(rows.stream().noneMatch(row -> ((Number)row.get("id")).longValue()==10002L));
        assertEquals(1,controller.products(()->"admin","REBUY","sn-10001",true).getData().size());
        assertEquals(1,controller.products(()->"admin","CONSUME","",true).getData().size());
    }
}
