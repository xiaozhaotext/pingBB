package com.macro.mall.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MallCategoryCatalogTest {
    JdbcTemplate jdbc;MallCategoryCatalog catalog;TransactionTemplate tx;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));catalog=new MallCategoryCatalog(jdbc,new ObjectMapper());
        jdbc.execute("CREATE TABLE pms_product_category(id BIGINT PRIMARY KEY,parent_id BIGINT,name VARCHAR(80),icon VARCHAR(200),sort INT,show_status INT)");
        jdbc.execute("CREATE TABLE pms_category_zone(category_id BIGINT,zone_code VARCHAR(20),PRIMARY KEY(category_id,zone_code))");
        jdbc.execute("CREATE TABLE pms_product_zone(product_id BIGINT PRIMARY KEY,zone_code VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_config(config_key VARCHAR(80) PRIMARY KEY,config_value TEXT,config_group VARCHAR(40),remark VARCHAR(200))");
        jdbc.execute("INSERT INTO pms_product_category VALUES(1,0,'消费分类',NULL,10,1),(2,1,'洗护',NULL,5,1),(3,1,'纸品',NULL,4,1),(4,0,'复购分类',NULL,9,1),(5,4,'复购商品',NULL,3,1),(6,0,'旅游',NULL,1,1),(7,6,'路线',NULL,1,1),(8,999,'独立分类',NULL,1,1),(9,1,'原专区隐藏',NULL,1,0)");
        jdbc.execute("INSERT INTO pms_category_zone VALUES(1,'CONSUME'),(2,'CONSUME'),(3,'CONSUME'),(4,'REBUY'),(5,'REBUY'),(6,'PICK'),(7,'PICK'),(8,'CONSUME'),(9,'CONSUME')");
        jdbc.execute("INSERT INTO pms_product_zone VALUES(101,'CONSUME'),(102,'REBUY'),(103,'PICK')");
    }
    MallCategoryCatalog.Snapshot update(long revision, MallCategoryCatalog.Change change,boolean reset) {return tx.execute(s->catalog.update(revision,change,reset));}
    @Test void onlyTwoZonesAreAutomaticallyAggregatedAndOrphansStaySelectable() {
        assertEquals(Set.of(1L,2L,3L,8L),flatten(catalog.tree("CONSUME")));
        assertEquals(Set.of(1L,2L,3L,4L,5L,8L),flatten(catalog.tree(null)));
        assertEquals(Set.of(4L,5L),flatten(catalog.tree("REBUY")));
        assertEquals(List.of(101L,102L),catalog.productIds(null));
        assertThrows(IllegalArgumentException.class,()->catalog.categoryIds("CONSUME",5));
        assertThrows(IllegalArgumentException.class,()->catalog.tree("unknown"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_config",Integer.class));
    }
    @Test void reparentRenameSortAndHideNeverChangeZoneSourceOrProducts() {
        var original=jdbc.queryForList("SELECT * FROM pms_product_category ORDER BY id");var zones=jdbc.queryForList("SELECT * FROM pms_category_zone ORDER BY category_id");var products=jdbc.queryForList("SELECT * FROM pms_product_zone ORDER BY product_id");
        update(0,new MallCategoryCatalog.Change(2L,"商城洗护标签",4L,100,true),false);
        assertEquals(Set.of(2L,4L,5L),Set.copyOf(catalog.categoryIds(null,4)));
        assertEquals(Set.of(1L,3L),Set.copyOf(catalog.categoryIds(null,1)));
        assertEquals(List.of(1L,2L,3L),catalog.categoryIds("CONSUME",1));
        assertEquals("洗护",catalog.tree("CONSUME").get(0).children().get(0).name());
        update(1,new MallCategoryCatalog.Change(4L,null,null,null,false),false);
        assertFalse(flatten(catalog.tree(null)).contains(2L));assertTrue(flatten(catalog.tree("CONSUME")).contains(2L));
        update(2,new MallCategoryCatalog.Change(2L,null,null,null,null),true);
        assertTrue(catalog.categoryIds(null,1).contains(2L));
        assertEquals(original,jdbc.queryForList("SELECT * FROM pms_product_category ORDER BY id"));assertEquals(zones,jdbc.queryForList("SELECT * FROM pms_category_zone ORDER BY category_id"));assertEquals(products,jdbc.queryForList("SELECT * FROM pms_product_zone ORDER BY product_id"));
    }
    @Test void upstreamAddRenameDeleteAreReflectedWithoutManualSync() {
        update(0,new MallCategoryCatalog.Change(2L,"自定义名称",null,null,null),false);
        jdbc.update("UPDATE pms_product_category SET name='更新后的原名称' WHERE id=3");
        jdbc.update("INSERT INTO pms_product_category VALUES(10,1,'新增分类',NULL,1,1)");jdbc.update("INSERT INTO pms_category_zone VALUES(10,'CONSUME')");
        assertTrue(catalog.categoryIds(null,1).contains(10L));
        assertTrue(catalog.snapshot().categories().stream().anyMatch(e->e.id()==3 && e.name().equals("更新后的原名称")));
        jdbc.update("DELETE FROM pms_category_zone WHERE category_id=2");assertFalse(flatten(catalog.tree(null)).contains(2L));
        assertFalse(flatten(catalog.tree(null)).contains(9L));
    }
    @Test void invalidCyclesForeignParentsAndStaleEditsRollback() {
        assertThrows(IllegalArgumentException.class,()->update(0,new MallCategoryCatalog.Change(1L,null,2L,null,null),false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_config",Integer.class));
        assertThrows(IllegalArgumentException.class,()->update(0,new MallCategoryCatalog.Change(2L,null,6L,null,null),false));
        update(0,new MallCategoryCatalog.Change(2L,"修改",null,null,null),false);
        assertThrows(IllegalArgumentException.class,()->update(0,new MallCategoryCatalog.Change(3L,"过期修改",null,null,null),false));
        assertEquals(1,catalog.snapshot().revision());
    }
    @Test void corruptGlobalMetadataCannotBreakZoneCategories() {
        jdbc.update("INSERT INTO sys_config VALUES(?,'bad-json','mall_catalog','test')",MallCategoryCatalog.CONFIG_KEY);
        assertThrows(IllegalStateException.class,()->catalog.tree(null));assertEquals(Set.of(4L,5L),flatten(catalog.tree("REBUY")));
    }
    @Test void concurrentEditsDoNotOverwriteEachOther() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> tasks=new ArrayList<>();
            for(long id:List.of(2L,3L))tasks.add(executor.submit(()->{start.await();try{update(0,new MallCategoryCatalog.Change(id,"并发修改",null,null,null),false);return true;}catch(IllegalArgumentException ex){return false;}}));
            start.countDown();int successes=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS))successes++;
            assertEquals(1,successes);assertEquals(1,catalog.snapshot().revision());
        } finally {executor.shutdownNow();}
    }
    Set<Long> flatten(List<MallCategoryCatalog.Node> nodes) {Set<Long> ids=new HashSet<>();for(var n:nodes){ids.add(n.id());ids.addAll(flatten(n.children()));}return ids;}
}
