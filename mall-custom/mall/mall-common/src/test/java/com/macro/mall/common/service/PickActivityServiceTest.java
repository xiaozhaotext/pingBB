package com.macro.mall.common.service;

import com.macro.mall.common.exception.ApiException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PickActivityServiceTest {
    private JdbcTemplate jdbc;
    private PickActivityService service;

    @BeforeEach
    void prepare() throws Exception {
        var data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE ums_menu(id BIGINT PRIMARY KEY,name VARCHAR(100),hidden INT)");
        // 直接执行本次交付迁移，验证可重复执行且无需赠品数据表。
        String migration = Files.readString(Path.of("../../deploy/migrations/20261113_pick_activities.sql"));
        for (int run = 0; run < 2; run++) for (String sql : migration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.execute("CREATE TABLE pms_product(id BIGINT PRIMARY KEY,name VARCHAR(100),pic VARCHAR(100),sub_title VARCHAR(100),price DECIMAL(10,2),brand_id BIGINT,publish_status INT,delete_status INT)");
        jdbc.execute("CREATE TABLE pms_product_zone(product_id BIGINT PRIMARY KEY,zone_code VARCHAR(20))");
        jdbc.execute("CREATE TABLE pms_brand(id BIGINT PRIMARY KEY,show_status INT)");
        jdbc.execute("CREATE TABLE tri_membership_product(product_id BIGINT PRIMARY KEY,enabled INT)");
        jdbc.execute("CREATE TABLE pms_sku_stock(id BIGINT AUTO_INCREMENT PRIMARY KEY,product_id BIGINT,price DECIMAL(10,2))");
        for (long id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO pms_product VALUES(?,?,'/product-images/a.png','介绍',3999,NULL,1,0)", id, "旅游正品" + id);
            jdbc.update("INSERT INTO pms_product_zone VALUES(?,'PICK')", id);
            jdbc.update("INSERT INTO tri_membership_product VALUES(?,1)", id);
            jdbc.update("INSERT INTO pms_sku_stock(product_id,price) VALUES(?,3999)", id);
        }
        var proxy = new ProxyFactory(new PickActivityService(jdbc));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        service = (PickActivityService) proxy.getProxy();
    }

    private PickActivityService.Activity config(Long id, boolean enabled, List<Long> products) {
        return new PickActivityService.Activity(id, "旅游活动详情1", "/product-images/activity.png", "活动规则\n第二行", enabled, 10, products);
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(long id, boolean publicView) {
        return ((List<Map<String,Object>>) service.detail(id, publicView).get("products")).stream()
                .map(row -> ((Number) row.get("id")).longValue()).toList();
    }

    @Test void savesActivityAndKeepsConfiguredProductOrder() {
        long id = service.save(config(null, true, List.of(3L,1L,4L,2L)));
        assertEquals(List.of(3L,1L,4L,2L), ids(id, true));
        assertEquals("活动规则\n第二行", service.detail(id, true).get("rules"));
        service.save(config(id, true, List.of(2L,3L)));
        assertEquals(List.of(2L,3L), ids(id, true));
        assertEquals(1, service.list(true).size());
    }

    @Test void disabledActivitiesAreNotPublicButRemainEditable() {
        long id = service.save(config(null, false, List.of(1L)));
        assertTrue(service.list(true).isEmpty());
        assertThrows(ApiException.class, () -> service.detail(id, true));
        assertEquals(List.of(1L), ids(id, false));
    }

    @Test void hidesUnpublishedWrongZoneAndInvalidPriceWithoutChangingSelectionOrder() {
        long id = service.save(config(null, true, List.of(1L,2L,3L,4L)));
        jdbc.update("UPDATE pms_product SET publish_status=0 WHERE id=1");
        jdbc.update("UPDATE pms_product_zone SET zone_code='REBUY' WHERE product_id=2");
        jdbc.update("UPDATE pms_sku_stock SET price=1 WHERE product_id=3");
        assertEquals(List.of(4L), ids(id, true));
        assertEquals(List.of(1L,2L,3L,4L), ids(id, false));
    }

    @Test void hidesDisabledBrandsDeletedProductsAndDisabledMembershipRoutes() {
        long id = service.save(config(null, true, List.of(1L,2L,3L)));
        jdbc.update("INSERT INTO pms_brand VALUES(10,0)");
        jdbc.update("UPDATE pms_product SET brand_id=10 WHERE id=1");
        jdbc.update("UPDATE pms_product SET delete_status=1 WHERE id=2");
        jdbc.update("UPDATE tri_membership_product SET enabled=0 WHERE product_id=3");
        assertTrue(ids(id, true).isEmpty());
    }

    @Test void rejectsInvalidAssociationsBeforeChangingExistingActivity() {
        long id = service.save(config(null, true, List.of(1L)));
        assertThrows(ApiException.class, () -> service.save(config(id, true, List.of(2L,2L))));
        assertThrows(ApiException.class, () -> service.save(config(id, true, List.of(999L))));
        jdbc.update("UPDATE pms_product_zone SET zone_code='CONSUME' WHERE product_id=2");
        assertThrows(ApiException.class, () -> service.save(config(id, true, List.of(2L))));
        assertEquals(List.of(1L), ids(id, true));
    }

    @Test void failedAssociationInsertRollsBackParentAndExistingAssociations() {
        long id = service.save(config(null, true, List.of(1L)));
        jdbc.execute("ALTER TABLE pms_pick_activity_product ADD CONSTRAINT test_reject CHECK(product_id<>2)");
        assertThrows(RuntimeException.class, () -> service.save(config(id, false, List.of(3L,2L))));
        assertEquals(List.of(1L), ids(id, true));
        assertThrows(RuntimeException.class, () -> service.save(config(null, true, List.of(2L))));
        assertEquals(1, service.list(false).size());
    }

    @Test void rejectsEmptyActivityAndUnsafeImage() {
        assertThrows(ApiException.class, () -> service.save(config(null, true, List.of())));
        assertThrows(ApiException.class, () -> service.save(new PickActivityService.Activity(null,"活动","javascript:alert(1)","规则",true,0,List.of(1L))));
        assertThrows(ApiException.class, () -> service.save(config(999L, true, List.of(1L))));
    }
}
