package com.macro.mall.portal.service;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import com.macro.mall.portal.controller.MemberContactController;
import com.macro.mall.portal.domain.UmsPointsAccount;
import com.macro.mall.model.*;
import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConsumptionAndContactTest {
 @Test void missingGiftPointsAreZeroAndOnlyPickCanGrantThem(){
  var cart=new com.macro.mall.portal.domain.CartPromotionItem();cart.setZoneCode("PICK");
  OmsOrderItem item=ReflectionTestUtils.invokeMethod(new OmsPortalOrderServiceImpl(),"toOrderItem",cart);
  assertEquals(0,item.getGiftIntegration());assertEquals(0,item.getGiftGrowth());
  cart.setIntegration(25);item=ReflectionTestUtils.invokeMethod(new OmsPortalOrderServiceImpl(),"toOrderItem",cart);assertEquals(25,item.getGiftIntegration());
  cart.setZoneCode("REBUY");item=ReflectionTestUtils.invokeMethod(new OmsPortalOrderServiceImpl(),"toOrderItem",cart);assertEquals(0,item.getGiftIntegration());
 }
 @Test void orderPointsUseTheCurrentWalletAndDoNotRequireTheRetiredLedger(){
  j.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY,integration BIGINT)");j.update("INSERT INTO ums_member VALUES(1,100)");
  j.execute("CREATE TABLE oms_order_point_usage(order_id BIGINT,member_id BIGINT,point_type VARCHAR(20),used_points BIGINT,deducted_amount DECIMAL(18,2))");
  var wallet=mock(PointsWalletService.class);var account=new UmsPointsAccount();account.setBalance(80L);
  when(wallet.changePoints(anyLong(),anyInt(),anyString(),anyLong(),anyString(),anyString(),isNull(),anyString())).thenReturn(com.macro.mall.common.api.CommonResult.success(null));
  when(wallet.getAccount(1L,1)).thenReturn(account);ReflectionTestUtils.setField(orders,"pointsWalletService",wallet);
  ReflectionTestUtils.invokeMethod(orders,"consumeOrderPoints",9L,1L,1,"PRODUCT",20L,new BigDecimal("20.00"));
  assertEquals(80L,j.queryForObject("SELECT integration FROM ums_member WHERE id=1",Long.class));
  assertEquals(20L,j.queryForObject("SELECT used_points FROM oms_order_point_usage WHERE order_id=9",Long.class));
  account.setBalance(100L);var order=new OmsOrder();order.setId(9L);order.setMemberId(1L);
  ReflectionTestUtils.invokeMethod(orders,"refundOrderPoints",order);
  assertEquals(100L,j.queryForObject("SELECT integration FROM ums_member WHERE id=1",Long.class));
  verify(wallet).changePoints(1L,1,"consume",-20L,"9","order:points:9:PRODUCT",null,"商品订单积分抵扣");
  verify(wallet).changePoints(1L,1,"earn",20L,"9","order:points-refund:9:PRODUCT",null,"未支付订单取消返还积分");
 }
 JdbcTemplate j; OmsPortalOrderServiceImpl orders;
 @BeforeEach void setup(){j=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:policy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));j.execute("DROP ALL OBJECTS");j.execute("CREATE TABLE pms_product_zone(product_id BIGINT,zone_code VARCHAR(20))");j.execute("CREATE TABLE sys_config(config_key VARCHAR(100),config_value VARCHAR(100))");j.update("INSERT INTO pms_product_zone VALUES(1,'CONSUME'),(2,'PICK')");orders=new OmsPortalOrderServiceImpl();ReflectionTestUtils.setField(orders,"jdbcTemplate",j);}
 @Test void mixedZonesRemainIndependent(){
  var settlement=new ZoneSettlementService(j).settle(List.of(new ZoneSettlementService.Line("PICK",new BigDecimal("100"),1,BigDecimal.ZERO,BigDecimal.ZERO),new ZoneSettlementService.Line("CONSUME",new BigDecimal("50"),1,BigDecimal.ZERO,BigDecimal.ZERO)),false,false,new ZoneSettlementService.Wallets(50,0,BigDecimal.ZERO));
  assertEquals(0,new BigDecimal("100.00").compareTo(settlement.getCashAmount()));assertEquals(0,new BigDecimal("50.00").compareTo(settlement.getProductPointAmount()));
 }
 @Test void travelCannotBypassMembershipPriceValidation(){
  var pools=mock(com.macro.mall.common.membership.TriPoolService.class);when(pools.enabled()).thenReturn(true);
  var fulfillment=new TriMembershipOrderService(pools,j,mock(PointsWalletService.class));
  var item=new OmsOrderItem();item.setZoneCode("PICK");item.setProductPrice(new BigDecimal("100"));
  assertThrows(ApiException.class,()->fulfillment.snapshot(new OmsOrder(),List.of(item)));
 }
 @Test void consumptionCanNeverBecomePartialCashPayment(){
  j.update("INSERT INTO sys_config VALUES('product.points.max_ratio','0.5')");
  var result=new ZoneSettlementService(j).settle(List.of(new ZoneSettlementService.Line("CONSUME",new BigDecimal("100.00"),1,BigDecimal.ZERO,BigDecimal.ZERO)),false,false,new ZoneSettlementService.Wallets(1000,0,BigDecimal.ZERO));
  var plan=result.getPlans().get(0);assertTrue(!plan.isPayable() || plan.getCashAmount().signum()==0,"Consumption zone must never request cash even with a partial points cap");
 }
 @Test void contactsAreMemberScopedAndEmailIsValidated(){
  j.execute("CREATE TABLE ums_member_contact(member_id BIGINT PRIMARY KEY,real_name VARCHAR(64),email VARCHAR(128),wechat_account VARCHAR(64))");
  var members=mock(UmsMemberService.class);var m=new UmsMember();m.setId(1L);when(members.getCurrentMember()).thenReturn(m);var controller=new MemberContactController(j,members);
  assertEquals(200,controller.save(Map.of("memberId",2,"real_name","测试姓名","email","qa@example.com","wechat_account","qa-user")).getCode());
  assertEquals("qa@example.com",controller.get().getData().get("email"));assertEquals(1L,j.queryForObject("SELECT member_id FROM ums_member_contact",Long.class));
  assertNotEquals(200,controller.save(Map.of("email","not an email")).getCode());assertEquals("qa@example.com",controller.get().getData().get("email"));
 }
}
