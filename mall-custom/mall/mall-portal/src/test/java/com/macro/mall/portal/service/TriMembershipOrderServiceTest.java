package com.macro.mall.portal.service;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TriMembershipOrderServiceTest {
 @Test void ordinary3999PickGoodsNeverActivateMembershipEvenWhenEnabled() {
  var pools=mock(TriPoolService.class);when(pools.enabled()).thenReturn(true);
  var jdbc=mock(JdbcTemplate.class);var wallets=mock(PointsWalletService.class);
  var service=new TriMembershipOrderService(pools,jdbc,wallets);
  var order=new OmsOrder();order.setPayAmount(new BigDecimal("3999"));
  var item=new OmsOrderItem();item.setProductPrice(new BigDecimal("3999"));item.setZoneCode("PICK");
  service.snapshot(order,List.of(item));assertFalse(service.fulfill(10));verifyNoInteractions(jdbc,wallets);
  verify(pools,never()).acceptPayment(anyString(),anyLong(),any(),any(),any());
 }
}
