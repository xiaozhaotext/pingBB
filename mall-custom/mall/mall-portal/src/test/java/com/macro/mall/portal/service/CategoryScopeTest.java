package com.macro.mall.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.service.MallCategoryCatalog;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.portal.controller.ProductZoneController;
import com.macro.mall.portal.service.impl.PmsPortalProductServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryScopeTest {
    JdbcTemplate jdbc;MallCategoryCatalog catalog;PmsProductMapper mapper;PmsPortalProductServiceImpl service;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa",""));
        // Keep the in-memory database across the individual JdbcTemplate connections.
        ((DriverManagerDataSource)jdbc.getDataSource()).setUrl(((DriverManagerDataSource)jdbc.getDataSource()).getUrl()+";DB_CLOSE_DELAY=-1");
        jdbc.execute("CREATE TABLE pms_product_category(id BIGINT PRIMARY KEY,parent_id BIGINT,name VARCHAR(80),icon VARCHAR(100),sort INT,show_status INT)");
        jdbc.execute("CREATE TABLE pms_category_zone(category_id BIGINT,zone_code VARCHAR(20))");
        jdbc.execute("CREATE TABLE sys_config(config_key VARCHAR(80) PRIMARY KEY,config_value TEXT,config_group VARCHAR(40),remark VARCHAR(100))");
        jdbc.execute("CREATE TABLE pms_product_zone(product_id BIGINT,zone_code VARCHAR(20),sort_order INT,consume_points_per_yuan DECIMAL(10,2))");
        jdbc.execute("INSERT INTO pms_product_category VALUES(10,0,'消费',NULL,10,1),(11,10,'消费下级',NULL,5,1),(20,0,'复购',NULL,10,1),(21,20,'复购下级',NULL,5,1),(40,0,'旅游',NULL,1,1)");
        jdbc.execute("INSERT INTO pms_category_zone VALUES(10,'CONSUME'),(11,'CONSUME'),(20,'REBUY'),(21,'REBUY'),(40,'PICK')");
        jdbc.execute("INSERT INTO pms_product_zone VALUES(100,'CONSUME',0,1),(200,'REBUY',0,1),(300,'PICK',0,1)");
        catalog=new MallCategoryCatalog(jdbc,new ObjectMapper());mapper=mock(PmsProductMapper.class);service=new PmsPortalProductServiceImpl();
        ReflectionTestUtils.setField(service,"mallCategories",catalog);ReflectionTestUtils.setField(service,"productMapper",mapper);
        when(mapper.selectByExample(any())).thenReturn(List.of());
    }
    @AfterEach void clearPagination(){PageHelper.clearPage();}
    Map<String,Object> criteria() {
        var captor=ArgumentCaptor.forClass(PmsProductExample.class);verify(mapper).selectByExample(captor.capture());
        Map<String,Object> values=new HashMap<>();for(var c:captor.getValue().getOredCriteria().get(0).getAllCriteria())values.put(c.getCondition(),c.getValue());return values;
    }
    @Test void searchPreservesParentDescendantsAndZoneProductBoundary() {
        service.search(null,null,10L,1,6,0,"CONSUME");
        var values=criteria();assertEquals(List.of(10L,11L),values.get("product_category_id in"));assertEquals(List.of(100L),values.get("id in"));
    }
    @Test void foreignCategoryIsRejectedBeforeProductQuery() {
        assertThrows(IllegalArgumentException.class,()->service.search(null,null,21L,1,6,0,"CONSUME"));verifyNoInteractions(mapper);
    }
    @Test void zoneSearchWithoutCategoryStillRestrictsProductIds() {
        service.search("商品",null,null,1,6,0,"REBUY");assertEquals(List.of(200L),criteria().get("id in"));
    }
    @Test void globalCategoryUsesMergedDisplayRelationsAndOnlyTwoZones() {
        catalog.update(0,new MallCategoryCatalog.Change(11L,"汇总标签",20L,null,true),false);
        service.search(null,null,20L,1,6,0,null);
        var values=criteria();assertEquals(Set.of(20L,21L,11L),Set.copyOf((List<Long>)values.get("product_category_id in")));assertEquals(List.of(100L,200L),values.get("id in"));
    }
    @Test void zoneListingRejectsForeignCategoryAndSupportsParentFilters() {
        jdbc.execute("CREATE TABLE pms_brand(id BIGINT,show_status INT)");
        jdbc.execute("CREATE TABLE pms_product(id BIGINT,name VARCHAR(80),pic VARCHAR(100),sub_title VARCHAR(80),price DECIMAL(10,2),sale INT,product_category_id BIGINT,publish_status INT,delete_status INT,brand_id BIGINT,keywords VARCHAR(80))");
        jdbc.execute("INSERT INTO pms_product VALUES(100,'消费商品','','',10,0,11,1,0,NULL,''),(200,'复购商品','','',10,0,21,1,0,NULL,'')");
        var controller=new ProductZoneController(jdbc);
        assertEquals(100L,((Number)controller.list("CONSUME",null,10L,"latest").getData().get(0).get("id")).longValue());
        assertThrows(IllegalArgumentException.class,()->controller.list("CONSUME",null,21L,"latest"));
        catalog.update(0,new MallCategoryCatalog.Change(11L,"仅改汇总",20L,null,false),false);
        assertEquals(1,controller.list("CONSUME",null,10L,"latest").getData().size());
    }
}
