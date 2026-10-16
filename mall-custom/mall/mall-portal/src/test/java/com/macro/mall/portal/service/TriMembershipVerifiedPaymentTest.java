package com.macro.mall.portal.service;
import com.macro.mall.common.membership.TriPoolRules;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TriMembershipVerifiedPaymentTest {
 JdbcTemplate jdbc;TriMembershipPurchaseService purchases;MembershipVerifiedPaymentHandler handler;Map<String,Object> order;
 @BeforeEach void setup(){
  jdbc=mock(JdbcTemplate.class);purchases=mock(TriMembershipPurchaseService.class);
  handler=new MembershipVerifiedPaymentHandler(jdbc,mock(PointsWalletService.class),mock(MemberIdentityService.class),mock(MembershipReferralRewardService.class));
  ReflectionTestUtils.setField(handler,"triPurchases",purchases);ReflectionTestUtils.setField(handler,"membershipV2Enabled",true);
  order=new HashMap<>();order.put("id",10L);order.put("member_id",1L);order.put("level","MEMBER");order.put("amount",TriPoolRules.PRICE);order.put("pay_channel","WECHAT");order.put("status","PENDING");
  when(jdbc.queryForList(anyString(),eq("MPtest"))).thenReturn(List.of(order));
 }
 MembershipPaymentGateway.VerifiedPayment payment(BigDecimal amount){return new MembershipPaymentGateway.VerifiedPayment("MPtest","transaction1","WECHAT",amount);}
 @Test void mismatchedAmountNeverReachesFulfillment(){assertThrows(RuntimeException.class,()->handler.accept(payment(new BigDecimal("1"))));verifyNoInteractions(purchases);}
 @Test void verifiedMemberOrderUsesNewEngineInsteadOfLegacyUpgrade(){
  when(jdbc.update(eq("UPDATE membership_purchase_order SET status='PAID',transaction_id=?,paid_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'"),eq("transaction1"),eq(10L))).thenReturn(1);
  handler.accept(payment(TriPoolRules.PRICE));verify(purchases).fulfill(order);
 }
 @Test void duplicateConfirmedNotificationDoesNotCreditAgain(){order.put("status","PAID");order.put("transaction_id","transaction1");handler.accept(payment(TriPoolRules.PRICE));verifyNoInteractions(purchases);}
 @Test void disablingV2CannotFulfillPendingNewOrdersAsLegacy(){ReflectionTestUtils.setField(handler,"membershipV2Enabled",false);assertThrows(IllegalStateException.class,()->handler.accept(payment(TriPoolRules.PRICE)));verifyNoInteractions(purchases);}
}
