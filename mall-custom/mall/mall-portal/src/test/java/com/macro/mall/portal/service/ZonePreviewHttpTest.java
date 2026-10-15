package com.macro.mall.portal.service;
import com.macro.mall.model.*;
import com.macro.mall.mapper.UmsIntegrationConsumeSettingMapper;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.controller.OmsPortalOrderController;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import com.macro.mall.portal.config.RechargeProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Actual production controller method and settlement service; catalog/member adapters use isolated SQL fixtures, not production authentication. */
@SpringBootTest(classes=ZonePreviewHttpTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.autoconfigure.exclude=com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure","logging.level.root=WARN"})
@org.springframework.test.context.ActiveProfiles("zone-preview-test")
public class ZonePreviewHttpTest {
 @Configuration @Profile("zone-preview-test")
 @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class,ManagementWebSecurityAutoConfiguration.class})
 @Import(Preview.class) static class App {
  @Bean DataSource ds(){return new DriverManagerDataSource("jdbc:h2:mem:zonepreview;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
 }
 @RestController @Profile("zone-preview-test") static class Preview {
  final OmsPortalOrderController controller;
  Preview(JdbcTemplate j){
   var orders=new OmsPortalOrderServiceImpl();var members=mock(UmsMemberService.class);var member=new UmsMember();member.setId(1L);when(members.getCurrentMember()).thenReturn(member);
   var cart=mock(OmsCartItemService.class);when(cart.listPromotion(eq(1L),anyList())).thenAnswer(call->{var rows=new ArrayList<CartPromotionItem>();for(var row:j.queryForList("SELECT * FROM qa_cart ORDER BY id")){if(!((List<?>)call.getArgument(1)).contains(((Number)row.get("id")).longValue()))continue;var item=new CartPromotionItem();item.setId(((Number)row.get("id")).longValue());item.setProductId(item.getId());item.setProductName("测试"+row.get("zone_code"));item.setZoneCode((String)row.get("zone_code"));item.setPrice((BigDecimal)row.get("price"));item.setQuantity(1);item.setReduceAmount(BigDecimal.ZERO);rows.add(item);}return rows;});
   var wallet=mock(PointsWalletService.class);when(wallet.getAccount(eq(1L),anyInt())).thenAnswer(call->{var a=new UmsPointsAccount();a.setAvailable(j.queryForObject("SELECT available FROM qa_wallet WHERE points_type=?",Long.class,(Object)call.getArgument(1)));return a;});
   var addresses=mock(UmsMemberReceiveAddressService.class);var address=new UmsMemberReceiveAddress();address.setId(1L);address.setDefaultStatus(1);address.setName("隔离测试会员");address.setPhoneNumber("13000000000");address.setProvince("测试省");address.setCity("测试市");address.setRegion("测试区");address.setDetailAddress("仅用于本地隔离联调");when(addresses.list()).thenReturn(List.of(address));
   var coupons=mock(UmsMemberCouponService.class);when(coupons.listCart(anyList(),eq(1))).thenReturn(List.of());
   ReflectionTestUtils.setField(orders,"jdbcTemplate",j);ReflectionTestUtils.setField(orders,"memberService",members);ReflectionTestUtils.setField(orders,"cartItemService",cart);ReflectionTestUtils.setField(orders,"pointsWalletService",wallet);ReflectionTestUtils.setField(orders,"memberReceiveAddressService",addresses);ReflectionTestUtils.setField(orders,"memberCouponService",coupons);ReflectionTestUtils.setField(orders,"integrationConsumeSettingMapper",mock(UmsIntegrationConsumeSettingMapper.class));ReflectionTestUtils.setField(orders,"zoneSettlementService",new ZoneSettlementService(j));ReflectionTestUtils.setField(orders,"rechargeService",new RechargeService(j,new RechargeProperties()));
   controller=new OmsPortalOrderController();ReflectionTestUtils.setField(controller,"portalOrderService",orders);
  }
  @PostMapping("/order/settlementPreview") Object preview(@RequestBody SettlementPreviewParam p){return controller.settlementPreview(p);}
 }
 @Autowired JdbcTemplate j;@Autowired TestRestTemplate http;
 @BeforeEach void setup(){j.execute("DROP ALL OBJECTS");j.execute("CREATE TABLE sys_config(config_key VARCHAR(100),config_value VARCHAR(100))");j.update("INSERT INTO sys_config VALUES('rebuy.points.ratio','0.30'),('product.points.max_ratio','1'),('product.points.yuan_per_point','1')");j.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT)");j.update("INSERT INTO ums_recharge_account VALUES(1,10000)");j.execute("CREATE TABLE qa_wallet(points_type INT PRIMARY KEY,available BIGINT)");j.update("INSERT INTO qa_wallet VALUES(1,50),(2,30)");j.execute("CREATE TABLE qa_cart(id BIGINT PRIMARY KEY,zone_code VARCHAR(16),price DECIMAL(18,2))");j.update("INSERT INTO qa_cart VALUES(1,'PICK',100),(2,'REBUY',100),(3,'CONSUME',50)");}
 Map data(boolean balance,boolean repurchase){var result=http.postForObject("/order/settlementPreview",Map.of("cartIds",List.of(1,2,3),"useBalance",balance,"useRepurchasePoints",repurchase),Map.class);assertEquals(200,result.get("code"));return (Map)result.get("data");}
 BigDecimal money(Map m,String key){return new BigDecimal(String.valueOf(m.get(key)));}
 @Test void actualHttpKeepsThreeWalletsAndCashSeparate(){var result=data(true,true);var settlement=(Map)result.get("zoneSettlement");assertEquals(0,new BigDecimal("70.00").compareTo(money(settlement,"cashAmount")));assertEquals(0,new BigDecimal("100.00").compareTo(money(settlement,"balanceAmount")));assertEquals(0,new BigDecimal("50.00").compareTo(money(settlement,"productPointAmount")));assertEquals(0,new BigDecimal("30.00").compareTo(money(settlement,"repurchasePointAmount")));assertEquals(10000L,j.queryForObject("SELECT balance_cents FROM ums_recharge_account",Long.class));}
 @Test void insufficientPointsBlockOnlyConsumptionAndDoNotBecomeCash(){j.update("UPDATE qa_wallet SET available=0 WHERE points_type=1");var settlement=(Map)data(false,false).get("zoneSettlement");var plans=(List<Map>)settlement.get("plans");var consumption=plans.stream().filter(p->"CONSUME".equals(p.get("zoneCode"))).findFirst().orElseThrow();assertEquals(false,consumption.get("payable"));assertEquals(0,new BigDecimal("200.00").compareTo(money(settlement,"cashAmount")));}
 public static void main(String[] args){var app=new SpringApplication(App.class);app.setAdditionalProfiles("zone-preview-test");var context=app.run(args);var fixture=new ZonePreviewHttpTest();fixture.j=context.getBean(JdbcTemplate.class);fixture.setup();System.out.println("ISOLATED_ZONE_PREVIEW_READY");}
}
