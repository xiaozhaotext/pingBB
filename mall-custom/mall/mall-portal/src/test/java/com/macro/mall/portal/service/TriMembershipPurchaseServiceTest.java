package com.macro.mall.portal.service;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.membership.*;
import com.macro.mall.portal.domain.MembershipPurchaseRequest;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TriMembershipPurchaseServiceTest {
 JdbcTemplate jdbc;TriPoolService pools;PointsWalletService wallets;TriMembershipPurchaseService service;
 @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);pools=mock(TriPoolService.class);wallets=mock(PointsWalletService.class);when(pools.enabled()).thenReturn(true);service=new TriMembershipPurchaseService(jdbc,pools,wallets);}
 MembershipPurchaseRequest request(String level){return new MembershipPurchaseRequest(1L,level,"WECHAT","WECHAT_H5","purchase_0001",null);}
 Map<String,Object> row(){var r=new HashMap<String,Object>();r.put("id",10L);r.put("member_id",1L);r.put("level","MEMBER");r.put("amount",TriPoolRules.PRICE);r.put("product_points",3999L);r.put("repurchase_points",3999L);r.put("referrer_member_id",2L);return r;}
 @Test void rejectsDirectPartnerAndCofounderPurchase(){assertThrows(IllegalArgumentException.class,()->service.prepare(1,request("PARTNER")));assertThrows(IllegalArgumentException.class,()->service.prepare(1,request("COFOUNDER")));verifyNoInteractions(jdbc);}
 @Test void paidMembersCannotPurchaseAgain(){when(pools.isMember(1)).thenReturn(true);assertThrows(IllegalArgumentException.class,()->service.prepare(1,request("MEMBER")));}
 @Test void pendingOrderBlocksAnotherRequest(){when(jdbc.queryForList("SELECT id FROM membership_purchase_order WHERE member_id=? AND status='PENDING' FOR UPDATE",Long.class,1L)).thenReturn(List.of(9L));assertThrows(IllegalArgumentException.class,()->service.prepare(1,request("MEMBER")));}
 @Test void standaloneOrderFreezesReferrerAndRulesWithoutCreditingAnything(){
  when(jdbc.queryForList("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?",Long.class,1L)).thenReturn(List.of(2L));
  when(jdbc.queryForMap(eq("SELECT * FROM membership_purchase_order WHERE merchant_order_no=?"),any())).thenReturn(row());
  var result=service.prepare(1,request("MEMBER"));assertEquals(10L,result.get("id"));
  verify(jdbc).update("INSERT INTO tri_membership_order(order_id,rule_version) VALUES(?,?)",10L,TriPoolRules.VERSION);
  verifyNoInteractions(wallets);verify(pools,never()).acceptPayment(anyString(),anyLong(),any(),any(),any());
 }
 @Test void onlyVersionedFullPriceSnapshotCanFulfill(){
  when(jdbc.queryForList("SELECT rule_version FROM tri_membership_order WHERE order_id=?",String.class,10L)).thenReturn(List.of(TriPoolRules.VERSION));
  var order=row();order.put("amount",new BigDecimal("3998"));assertThrows(IllegalStateException.class,()->service.fulfill(order));
  order.put("amount",TriPoolRules.PRICE);order.put("repurchase_points",4000L);assertThrows(IllegalStateException.class,()->service.fulfill(order));
  verifyNoInteractions(wallets);
 }
 @Test void verifiedPurchaseUsesOneThreePoolTransactionAndPropagatesWalletFailure(){
  when(jdbc.queryForList("SELECT rule_version FROM tri_membership_order WHERE order_id=?",String.class,10L)).thenReturn(List.of(TriPoolRules.VERSION));
  when(wallets.changePoints(anyLong(),anyInt(),anyString(),anyLong(),anyString(),anyString(),isNull(),anyString())).thenReturn(CommonResult.failed("wallet failed"));
  doAnswer(inv->{TriPoolService.PointsCredit credit=inv.getArgument(4);credit.post(1L,1,3999,"key");return true;}).when(pools).acceptPayment(eq("MEMBERSHIP:10"),eq(1L),eq(2L),eq(TriPoolRules.PRICE),any());
  assertThrows(IllegalStateException.class,()->service.fulfill(row()));
 }
 @Test void disabledRulesRejectPendingNewPurchase(){when(pools.enabled()).thenReturn(false);assertThrows(IllegalArgumentException.class,()->service.prepare(1,request("MEMBER")));assertThrows(IllegalStateException.class,()->service.fulfill(row()));}
}
