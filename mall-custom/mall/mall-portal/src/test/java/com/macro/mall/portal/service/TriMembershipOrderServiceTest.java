package com.macro.mall.portal.service;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.common.membership.TriPoolRules;
import com.macro.mall.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TriMembershipOrderServiceTest {
 @Test void paidPickRouteGrantsMembershipOnce() {
  var pools=mock(TriPoolService.class);when(pools.enabled()).thenReturn(true);
  var jdbc=mock(JdbcTemplate.class);var wallets=mock(PointsWalletService.class);
  var service=new TriMembershipOrderService(pools,jdbc,wallets);
  var order=new OmsOrder();order.setId(10L);order.setMemberId(1L);
  order.setTotalAmount(TriPoolRules.PRICE);order.setPayAmount(TriPoolRules.PRICE);
  var item=new OmsOrderItem();item.setProductId(7L);item.setProductQuantity(1);
  item.setProductPrice(TriPoolRules.PRICE);item.setZoneCode("PICK");
  when(jdbc.queryForList(anyString(),eq(Long.class),eq(1L))).thenReturn(List.of(2L));
  when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L))).thenReturn(0);
  when(jdbc.queryForObject(contains("FROM pms_product_zone"),eq(Integer.class),eq(7L))).thenReturn(1);
  service.snapshot(order,List.of(item));
  verify(jdbc).update(contains("tri_order_snapshot"),eq(10L),eq(1L),eq(7L),eq(2L),eq(TriPoolRules.PRICE),eq(TriPoolRules.VERSION));
  when(jdbc.queryForList(contains("FROM tri_order_snapshot WHERE order_id"),eq(10L)))
      .thenReturn(List.of(Map.of("member_id",1L,"referrer_id",2L,"price",TriPoolRules.PRICE,"rule_version",TriPoolRules.VERSION)));
  when(pools.acceptPayment(eq("ORDER:10"),eq(1L),eq(2L),eq(TriPoolRules.PRICE),any())).thenReturn(true);
  assertTrue(service.fulfill(10));
  verify(pools).acceptPayment(eq("ORDER:10"),eq(1L),eq(2L),eq(TriPoolRules.PRICE),any());
 }
}
