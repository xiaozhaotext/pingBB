package com.macro.mall.portal.service.impl;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.model.OmsOrderItem;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class OrderGiftPersistenceTests {
 @Test void actualInsertAndPaymentReadPreserveGiftZoneQuantityAndPaidAmount() throws Exception {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:giftpersist;MODE=MySQL;DB_CLOSE_DELAY=-1");
  var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE oms_order(id BIGINT PRIMARY KEY,order_sn VARCHAR(64),coupon_id BIGINT,integration INT,member_id BIGINT,pay_amount DECIMAL(10,2))");
  jdbc.execute("CREATE TABLE oms_order_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT,order_sn VARCHAR(64),product_id BIGINT,product_pic VARCHAR(255),product_name VARCHAR(255),product_brand VARCHAR(255),product_sn VARCHAR(64),product_price DECIMAL(10,2),product_quantity INT,product_sku_id BIGINT,product_category_id BIGINT,product_sku_code VARCHAR(64),promotion_name VARCHAR(255),promotion_amount DECIMAL(10,2),coupon_amount DECIMAL(10,2),integration_amount DECIMAL(10,2),real_amount DECIMAL(10,2),product_attr VARCHAR(500),zone_code VARCHAR(16),gift_integration INT DEFAULT 0,gift_growth INT DEFAULT 0)");
  var f=new SqlSessionFactoryBean();f.setDataSource(ds);
  f.setMapperLocations(new ClassPathResource("com/macro/mall/mapper/OmsOrderMapper.xml"),new ClassPathResource("com/macro/mall/mapper/OmsOrderItemMapper.xml"),new ClassPathResource("dao/PortalOrderItemDao.xml"),new ClassPathResource("dao/PortalOrderDao.xml"));
  var sessions=new SqlSessionTemplate(f.getObject());
  jdbc.update("INSERT INTO oms_order VALUES(10,'DEMO',NULL,200,1,200.00)");
  OmsOrderItem item=new OmsOrderItem();item.setOrderId(10L);item.setProductId(99L);item.setProductSkuId(90L);item.setZoneCode("PICK");item.setGiftIntegration(100);item.setGiftGrowth(0);item.setProductQuantity(2);
  assertEquals(1,sessions.getMapper(PortalOrderItemDao.class).insertList(List.of(item)));
  var detail=sessions.getMapper(PortalOrderDao.class).getDetail(10L);
  assertEquals(0,new BigDecimal("200.00").compareTo(detail.getPayAmount()));
  assertEquals(1,detail.getOrderItemList().size());
  var stored=detail.getOrderItemList().get(0);
  assertEquals("PICK",stored.getZoneCode());assertEquals(100,stored.getGiftIntegration());assertEquals(2,stored.getProductQuantity());
 }
}
