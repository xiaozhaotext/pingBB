package com.macro.mall.portal.service;

import com.macro.mall.portal.controller.DividendPoolController;
import com.macro.mall.model.UmsMember;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated acceptance probes against the current production service and controller. */
class DividendAcceptanceTest extends FinancialIntegrityTest {
 private final LocalDate day=LocalDate.of(2026,10,16);
 private DividendPoolController controller() {
  var c=new DividendPoolController();var members=mock(UmsMemberService.class);
  var member=new UmsMember();member.setId(1L);when(members.getCurrentMember()).thenReturn(member);
  ReflectionTestUtils.setField(c,"memberService",members);ReflectionTestUtils.setField(c,"jdbc",j);return c;
 }
 @Test void partnerAndCofounderReceiveOneToThreeShares() {
  j.update("UPDATE dividend_pool SET pool_balance=400");j.update("UPDATE ums_member_identity SET identity_type=2 WHERE member_id=2");
  assertEquals(2,tx.execute(s->dividends.distributeDaily(day)).intValue());
  assertEquals(new BigDecimal("100.00"),j.queryForObject("SELECT dividend_amount FROM member_dividend_ledger WHERE member_id=1",BigDecimal.class));
  assertEquals(new BigDecimal("300.00"),j.queryForObject("SELECT dividend_amount FROM member_dividend_ledger WHERE member_id=2",BigDecimal.class));
 }
 @Test void cumulativeCapAndReferralIncreaseApplyAcrossDates() {
  j.update("UPDATE dividend_pool SET pool_balance=10000");
  assertEquals(2,tx.execute(s->dividends.distributeDaily(day)).intValue());
  assertEquals(0,tx.execute(s->dividends.distributeDaily(day.plusDays(1))).intValue());
  j.update("INSERT INTO ums_member_recommend VALUES(1)");
  assertEquals(1,tx.execute(s->dividends.distributeDaily(day.plusDays(1))).intValue());
  assertEquals(new BigDecimal("2400.00"),j.queryForObject("SELECT SUM(dividend_amount) FROM member_dividend_ledger WHERE member_id=1",BigDecimal.class));
 }
 @Test void dailyBaseLimitsPaymentAndRetainsPoolBalance() {
  j.update("UPDATE dividend_pool SET pool_balance=100");j.update("INSERT INTO sys_config VALUES('dividend.daily.base','3.129')");
  assertEquals(2,tx.execute(s->dividends.distributeDaily(day)).intValue());
  assertEquals(new BigDecimal("93.76"),j.queryForObject("SELECT pool_balance FROM dividend_pool",BigDecimal.class));
 }
 @Test void disabledSwitchesRejectPostingAndConsumerMutationIsForbidden() {
  ReflectionTestUtils.setField(dividends,"payoutEnabled",false);
  assertThrows(IllegalStateException.class,()->tx.execute(s->dividends.distributeDaily(day)).intValue());
  ReflectionTestUtils.setField(dividends,"payoutEnabled",true);j.update("UPDATE sys_config SET config_value='false'");
  assertThrows(IllegalStateException.class,()->tx.execute(s->dividends.distributeDaily(day)).intValue());
  var c=controller();assertEquals(403,c.distribute(Map.of()).getCode());assertEquals(403,c.contribution(Map.of()).getCode());assertEquals(403,c.allocate(Map.of()).getCode());
 }
 @Test void mobileInfoReturnsConfiguredCapAndOnlyOwnRecords() {
  for(int i=0;i<12;i++)j.update("INSERT INTO ums_member_recommend VALUES(1)");
  var c=controller();var info=c.info().getData();assertEquals(new BigDecimal("12000.00"),info.get("capAmount"));assertEquals(1,info.get("shares"));assertEquals(12,info.get("referralCount"));
  j.execute("ALTER TABLE member_dividend_ledger ADD create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
  j.update("UPDATE dividend_pool SET pool_balance=100");tx.execute(s->dividends.distributeDaily(day));
  var records=c.records(1,20).getData();assertEquals(1,records.size());assertEquals(new BigDecimal("50.00"),records.get(0).get("dividend_amount"));
  assertTrue(c.records(2,1).getData().isEmpty());
 }
 @Test void referralLimitConfigurationExposesDisplayCalculationMismatch() {
  j.update("INSERT INTO sys_config VALUES('dividend.cap.max_referrals','0')");j.update("INSERT INTO ums_member_recommend VALUES(1)");
  assertEquals(new BigDecimal("2400.00"),controller().info().getData().get("capAmount"));
  j.update("UPDATE dividend_pool SET pool_balance=10000");tx.execute(s->dividends.distributeDaily(day));
  assertEquals(new BigDecimal("1200.00"),j.queryForObject("SELECT cap_amount FROM member_dividend_ledger WHERE member_id=1",BigDecimal.class));
 }
 @Test void infoEnabledFlagDoesNotReflectDeploymentPayoutGate() {
  ReflectionTestUtils.setField(dividends,"payoutEnabled",false);
  assertEquals(true,controller().info().getData().get("distributionEnabled"));
  assertThrows(IllegalStateException.class,()->tx.execute(s->dividends.distributeDaily(day)).intValue());
 }
 @Test void alreadyCappedMembersStillCountInShareDenominator() {
  j.update("INSERT INTO member_dividend_ledger VALUES(1,?,1,1200,1200,1200,'old')",day.minusDays(1));
  j.update("UPDATE dividend_pool SET pool_balance=100");
  assertEquals(1,tx.execute(s->dividends.distributeDaily(day)).intValue());
  assertEquals(new BigDecimal("50.00"),j.queryForObject("SELECT pool_balance FROM dividend_pool",BigDecimal.class));
 }
}
