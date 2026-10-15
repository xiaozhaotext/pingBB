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
 JdbcTemplate j; OmsPortalOrderServiceImpl orders;
 @BeforeEach void setup(){j=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:policy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));j.execute("DROP ALL OBJECTS");j.execute("CREATE TABLE pms_product_zone(product_id BIGINT,zone_code VARCHAR(20))");j.execute("CREATE TABLE sys_config(config_key VARCHAR(100),config_value VARCHAR(100))");j.update("INSERT INTO pms_product_zone VALUES(1,'CONSUME'),(2,'PICK')");orders=new OmsPortalOrderServiceImpl();ReflectionTestUtils.setField(orders,"jdbcTemplate",j);}
 @Test void consumptionCannotBeMixedWithCashZoneGoods(){assertTrue((Boolean)ReflectionTestUtils.invokeMethod(orders,"consumptionCart",List.of(1L)));assertFalse((Boolean)ReflectionTestUtils.invokeMethod(orders,"consumptionCart",List.of(2L)));assertThrows(ApiException.class,()->ReflectionTestUtils.invokeMethod(orders,"consumptionCart",List.of(1L,2L)));}
 @Test void explicitlyMappedTravelMustUseMembershipFulfillment(){j.update("INSERT INTO sys_config VALUES('membership.partner.product_id','2')");assertThrows(ApiException.class,()->ReflectionTestUtils.invokeMethod(orders,"consumptionCart",List.of(2L)));}
 @Test void consumptionUsesFullProductPointAmountDespitePickCap(){
  j.update("INSERT INTO sys_config VALUES('product.points.max_ratio','0.5')");
  var wallet=mock(PointsWalletService.class);var a=new UmsPointsAccount();a.setAvailable(3999L);when(wallet.getAccount(1L,1)).thenReturn(a);ReflectionTestUtils.setField(orders,"pointsWalletService",wallet);
  var member=new UmsMember();member.setId(1L);var item=new OmsOrderItem();item.setProductId(1L);item.setProductPrice(new BigDecimal("3999.00"));item.setProductQuantity(1);item.setPromotionAmount(BigDecimal.ZERO);item.setCouponAmount(BigDecimal.ZERO);
  BigDecimal amount=ReflectionTestUtils.invokeMethod(orders,"getUseIntegrationAmount",3999,List.of(item),member);assertEquals(0,amount.compareTo(new BigDecimal("3999.00")));
  item.setProductId(2L);assertThrows(ApiException.class,()->ReflectionTestUtils.invokeMethod(orders,"getUseIntegrationAmount",3999,List.of(item),member));
 }
 @Test void contactsAreMemberScopedAndEmailIsValidated(){
  j.execute("CREATE TABLE ums_member_contact(member_id BIGINT PRIMARY KEY,real_name VARCHAR(64),email VARCHAR(128),wechat_account VARCHAR(64))");
  var members=mock(UmsMemberService.class);var m=new UmsMember();m.setId(1L);when(members.getCurrentMember()).thenReturn(m);var controller=new MemberContactController(j,members);
  assertEquals(200,controller.save(Map.of("memberId",2,"real_name","测试姓名","email","qa@example.com","wechat_account","qa-user")).getCode());
  assertEquals("qa@example.com",controller.get().getData().get("email"));assertEquals(1L,j.queryForObject("SELECT member_id FROM ums_member_contact",Long.class));
  assertNotEquals(200,controller.save(Map.of("email","not an email")).getCode());assertEquals("qa@example.com",controller.get().getData().get("email"));
 }
}
