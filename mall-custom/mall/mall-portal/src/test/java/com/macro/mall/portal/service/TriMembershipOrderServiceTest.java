package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.membership.*;
import com.macro.mall.model.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TriMembershipOrderServiceTest {
    TriPoolService pools;JdbcTemplate jdbc;PointsWalletService wallets;TriMembershipOrderService service;
    @BeforeEach void setup() {
        pools=mock(TriPoolService.class);jdbc=mock(JdbcTemplate.class);wallets=mock(PointsWalletService.class);
        when(pools.enabled()).thenReturn(true);service=new TriMembershipOrderService(pools,jdbc,wallets);
    }
    OmsOrder order() { var o=new OmsOrder();o.setId(10L);o.setMemberId(1L);o.setPayAmount(TriPoolRules.PRICE);return o; }
    OmsOrderItem item() {var i=new OmsOrderItem();i.setProductId(100L);i.setProductQuantity(1);i.setProductPrice(TriPoolRules.PRICE);i.setZoneCode("PICK");return i;}
    Map<String,Object> paidRow(String cash,String balance) {
        var row=new HashMap<String,Object>();row.put("member_id",1L);row.put("rule_version",TriPoolRules.VERSION);
        row.put("status",1);row.put("price",TriPoolRules.PRICE);row.put("pay_amount",new BigDecimal(cash));row.put("balance_amount",new BigDecimal(balance));return row;
    }
    @Test void ordinary3999ProductDoesNotActivateMembership() {
        service.snapshot(order(),List.of(item()));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
        assertFalse(service.fulfill(10));verify(pools,never()).acceptPayment(anyString(),anyLong(),any(),any(),any());
    }
    @Test void cardMustBeStandaloneSinglePickItem() {
        when(jdbc.queryForList("SELECT product_id FROM tri_membership_product WHERE enabled=1",Long.class)).thenReturn(List.of(100L));
        var item=item();item.setZoneCode("REBUY");
        assertThrows(ApiException.class,()->service.snapshot(order(),List.of(item)));
        item.setZoneCode("PICK");item.setProductQuantity(2);
        assertThrows(ApiException.class,()->service.snapshot(order(),List.of(item)));
        assertThrows(ApiException.class,()->service.snapshot(order(),List.of(item(),item())));
    }
    @Test void pendingMembershipCardPreventsSecondCheckout() {
        when(jdbc.queryForList("SELECT product_id FROM tri_membership_product WHERE enabled=1",Long.class)).thenReturn(List.of(100L));
        when(jdbc.queryForList("SELECT s.order_id FROM tri_order_snapshot s JOIN oms_order o ON o.id=s.order_id WHERE s.member_id=? AND o.status IN (0,1,2,3) FOR UPDATE",Long.class,1L)).thenReturn(List.of(9L));
        assertThrows(ApiException.class,()->service.snapshot(order(),List.of(item())));
    }
    @Test void snapshotsDirectReferrerWithoutAssumingRegistrationIsPaid() {
        when(jdbc.queryForList("SELECT product_id FROM tri_membership_product WHERE enabled=1",Long.class)).thenReturn(List.of(100L));
        when(jdbc.queryForList("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?",Long.class,1L)).thenReturn(List.of(2L));
        service.snapshot(order(),List.of(item()));
        verify(jdbc).update("INSERT INTO tri_order_snapshot(order_id,member_id,product_id,referrer_id,price,rule_version) VALUES(?,?,?,?,?,?)",10L,1L,100L,2L,TriPoolRules.PRICE,TriPoolRules.VERSION);
    }
    @Test void paymentAmountMismatchNeverGrantsAnything() {
        when(jdbc.queryForList(anyString(),eq(10L))).thenReturn(List.of(paidRow("3999","0")));
        when(jdbc.queryForObject("SELECT COALESCE(SUM(paid_amount),0) FROM oms_payment_confirmation WHERE order_id=?",BigDecimal.class,10L)).thenReturn(new BigDecimal("3998"));
        assertThrows(IllegalStateException.class,()->service.fulfill(10));
        verifyNoInteractions(wallets);verify(pools,never()).acceptPayment(anyString(),anyLong(),any(),any(),any());
    }
    @Test void verifiedCashPaymentIsPassedWithFrozenMembershipBasis() {
        when(jdbc.queryForList(anyString(),eq(10L))).thenReturn(List.of(paidRow("3999","0")));
        when(jdbc.queryForObject("SELECT COALESCE(SUM(paid_amount),0) FROM oms_payment_confirmation WHERE order_id=?",BigDecimal.class,10L)).thenReturn(TriPoolRules.PRICE);
        assertTrue(service.fulfill(10));
        verify(pools).acceptPayment(eq("ORDER:10"),eq(1L),isNull(),eq(TriPoolRules.PRICE),any());
    }
    @Test void balancePaymentRequiresItsOriginalPrincipalLedger() {
        when(jdbc.queryForList(anyString(),eq(10L))).thenReturn(List.of(paidRow("0","3999")));
        when(jdbc.queryForObject("SELECT COALESCE(SUM(paid_amount),0) FROM oms_payment_confirmation WHERE order_id=?",BigDecimal.class,10L)).thenReturn(BigDecimal.ZERO);
        assertThrows(IllegalStateException.class,()->service.fulfill(10));
        when(jdbc.queryForObject("SELECT COALESCE(-SUM(amount_cents),0) FROM ums_recharge_ledger WHERE member_id=? AND biz_id=? AND change_type IN ('order_pay','order_refund')",Long.class,1L,"10")).thenReturn(399900L);
        assertTrue(service.fulfill(10));
    }
    @Test void disabledVersionDoesNotTouchNewSchema() {
        when(pools.enabled()).thenReturn(false);
        service.snapshot(order(),List.of(item()));assertFalse(service.fulfill(10));verifyNoInteractions(jdbc,wallets);
    }
}
