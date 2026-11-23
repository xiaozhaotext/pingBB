package com.macro.mall.portal.service;
import com.macro.mall.portal.service.impl.EarningsServiceImpl;
import com.macro.mall.portal.dao.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionFactoryBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class FinancialIntegrityTest {
 JdbcTemplate j; TransactionTemplate tx; EarningsServiceImpl service; DividendDistributionService dividends;
 @BeforeEach void setup() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:integrity;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
  j=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));j.execute("DROP ALL OBJECTS");
  var c=new org.apache.ibatis.session.Configuration();c.setMapUnderscoreToCamelCase(true);
  c.addMapper(UmsEarningsMapper.class);c.addMapper(UmsWithdrawApplyMapper.class);c.addMapper(UmsEarningsLedgerMapper.class);
  var f=new SqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(c);var sql=new SqlSessionTemplate(f.getObject());
  service=new EarningsServiceImpl();ReflectionTestUtils.setField(service,"jdbc",j);
  ReflectionTestUtils.setField(service,"earningsMapper",sql.getMapper(UmsEarningsMapper.class));
  ReflectionTestUtils.setField(service,"withdrawMapper",sql.getMapper(UmsWithdrawApplyMapper.class));
  dividends=new DividendDistributionService(j,null,new MemberEarningsCreditService(j));ReflectionTestUtils.setField(dividends,"payoutEnabled",true);
  j.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY)");j.update("INSERT INTO ums_member VALUES(1),(2)");
  j.execute("CREATE TABLE sys_config(config_key VARCHAR(100),config_value VARCHAR(100))");j.update("INSERT INTO sys_config VALUES('dividend.distribution.enabled','true')");
  j.execute("CREATE TABLE ums_earnings(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT UNIQUE,total_earnings DECIMAL(18,2),available_earnings DECIMAL(18,2),frozen_earnings DECIMAL(18,2),withdrawn DECIMAL(18,2),version BIGINT)");
  j.update("INSERT INTO ums_earnings VALUES(1,1,100,100,0,0,0),(2,2,0,0,0,0,0)");
  j.execute("CREATE TABLE ums_withdraw_apply(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,idempotency_key VARCHAR(64),amount DECIMAL(18,2),fee_rate DECIMAL(10,6),fee_amount DECIMAL(18,2),actual_amount DECIMAL(18,2),status INT,simulate_flag INT,audit_remark VARCHAR(255),audit_time TIMESTAMP,UNIQUE(member_id,idempotency_key))");
  j.execute("CREATE TABLE ums_earnings_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(50),amount DECIMAL(18,2),balance_after DECIMAL(18,2),biz_id VARCHAR(100),idempotency_key VARCHAR(100) UNIQUE,remark VARCHAR(255))");
  j.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT)");
  j.execute("CREATE TABLE ums_recharge_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(32),amount_cents BIGINT,balance_after_cents BIGINT,biz_id VARCHAR(64),idempotency_key VARCHAR(64) UNIQUE,remark VARCHAR(255))");
  j.execute("CREATE TABLE dividend_pool(id INT PRIMARY KEY,pool_balance DECIMAL(18,2),total_out DECIMAL(18,2))");j.update("INSERT INTO dividend_pool VALUES(1,0.05,0)");
  j.execute("CREATE TABLE ums_member_identity(member_id BIGINT,identity_type INT,status INT)");j.update("INSERT INTO ums_member_identity VALUES(1,1,1),(2,1,1)");
  j.execute("CREATE TABLE ums_member_recommend(inviter_id BIGINT)");
  j.execute("CREATE TABLE member_dividend_ledger(member_id BIGINT,stat_date DATE,shares INT,dividend_amount DECIMAL(18,2),cap_amount DECIMAL(18,2),cumulative_received DECIMAL(18,2),calc_batch VARCHAR(100),UNIQUE(member_id,stat_date))");
  j.execute("CREATE TABLE dividend_pool_ledger(change_amount DECIMAL(18,2),balance_after DECIMAL(18,2),source_type VARCHAR(100),source_id BIGINT,remark VARCHAR(255))");
 }
 @Test void concurrentRetriesFreezeExactlyOnce() throws Exception {
  var pool=Executors.newFixedThreadPool(8);try {
   var start=new CountDownLatch(1);var tasks=new java.util.ArrayList<Future<Long>>();
   for(int i=0;i<8;i++)tasks.add(pool.submit(()->{start.await();return tx.execute(s->{var r=service.applyWithdraw(1L,new BigDecimal("100.00"),"withdraw-test-1");assertEquals(200,r.getCode());return r.getData().getId();});}));
   start.countDown();Long id=tasks.get(0).get(20,TimeUnit.SECONDS);for(var t:tasks)assertEquals(id,t.get(20,TimeUnit.SECONDS));
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_withdraw_apply",Integer.class));
   assertEquals(new BigDecimal("0.00"),j.queryForObject("SELECT frozen_earnings FROM ums_earnings WHERE member_id=1",BigDecimal.class));
   assertEquals(new BigDecimal("100.00"),j.queryForObject("SELECT withdrawn FROM ums_earnings WHERE member_id=1",BigDecimal.class));
   assertEquals(10000L,j.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=1",Long.class));
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_recharge_ledger",Integer.class));
   assertEquals(new BigDecimal("0.00"),j.queryForObject("SELECT fee_amount FROM ums_withdraw_apply WHERE id=?",BigDecimal.class,id));
   assertNotEquals(200,tx.execute(s->service.applyWithdraw(1L,new BigDecimal("11"),"withdraw-test-1")).getCode());
  }finally{pool.shutdownNow();}
 }
 @Test void invalidAmountsNeverFreeze(){
  for(BigDecimal a:new BigDecimal[]{null,new BigDecimal("1.001"),BigDecimal.ZERO,new BigDecimal("-1")})assertNotEquals(200,tx.execute(s->service.applyWithdraw(1L,a,"withdraw-test-2")).getCode());
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM ums_withdraw_apply",Integer.class));
 }
 @Test void bothWithdrawalRoutesFollowLiveThresholdAndKeepReplayStable() {
  j.execute("CREATE TABLE ums_wallet_withdrawal(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,request_id VARCHAR(64),gross_cents BIGINT,fee_cents BIGINT,net_cents BIGINT,fee_rate DECIMAL(10,6),recipient_name VARCHAR(80),channel VARCHAR(20),bank_name VARCHAR(120),bank_branch VARCHAR(160),recipient_account VARCHAR(160),status VARCHAR(20) DEFAULT 'PENDING',decision_note VARCHAR(200),payout_reference VARCHAR(100),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,decided_at TIMESTAMP,UNIQUE(member_id,request_id))");
  j.update("INSERT INTO ums_recharge_account VALUES(1,10000)");
  var wallet=new WalletWithdrawalService(j);
  var request=new WalletWithdrawalService.Request("12.35","wallet-policy-test-01","测试用户","WECHAT",null,null,"test-account");
  assertThrows(IllegalArgumentException.class,()->tx.execute(s->wallet.apply(1,request)));
  assertNotEquals(200,tx.execute(s->service.applyWithdraw(1L,new BigDecimal("12.35"),"earnings-policy-test-01")).getCode());
  j.update("INSERT INTO sys_config VALUES('withdraw.threshold_rule','CUSTOM:12.34')");
  assertNotEquals(200,tx.execute(s->service.applyWithdraw(1L,new BigDecimal("12.33"),"earnings-policy-test-01")).getCode());
  assertEquals(200,tx.execute(s->service.applyWithdraw(1L,new BigDecimal("12.35"),"earnings-policy-test-01")).getCode());
  var applied=tx.execute(s->wallet.apply(1,request));
  assertEquals(7L,((Number)applied.get("fee_cents")).longValue());
  assertEquals(1228L,((Number)applied.get("net_cents")).longValue());
  assertEquals(10000L,j.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=1",Long.class));
  j.update("UPDATE sys_config SET config_value='HUNDRED' WHERE config_key='withdraw.threshold_rule'");
  assertEquals(applied.get("id"),tx.execute(s->wallet.apply(1,request)).get("id"));
  assertEquals(200,tx.execute(s->service.applyWithdraw(1L,new BigDecimal("12.35"),"earnings-policy-test-01")).getCode());
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_wallet_withdrawal",Integer.class));
  j.update("UPDATE sys_config SET config_value='NONE' WHERE config_key='withdraw.threshold_rule'");
  assertEquals(200,tx.execute(s->service.applyWithdraw(1L,new BigDecimal("0.01"),"earnings-policy-test-02")).getCode());
  var penny=new WalletWithdrawalService.Request("0.01","wallet-policy-test-02","测试用户","WECHAT",null,null,"test-account");
  tx.execute(s->wallet.apply(1,penny));
  assertEquals(10000L,j.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=1",Long.class));
 }
 @Test void invalidThresholdFailsClosedAndHundredRuleRejectsNonMultiples() {
  var policy=new WithdrawalPolicy(j);
  assertNotNull(policy.current().violation(new BigDecimal("101")));
  assertNull(policy.current().violation(new BigDecimal("100.00")));
  assertNotNull(policy.current().violation(new BigDecimal("50000.01")));
  j.update("INSERT INTO sys_config VALUES('withdraw.threshold_rule','CUSTOM:0')");
  assertThrows(IllegalArgumentException.class,policy::current);
 }
 @Test void fiveCentsCannotPaySixCents(){
  assertEquals(2,tx.execute(s->dividends.distributeDaily(LocalDate.of(2026,10,15))).intValue());
  assertEquals(new BigDecimal("0.04"),j.queryForObject("SELECT SUM(dividend_amount) FROM member_dividend_ledger",BigDecimal.class));
  assertEquals(new BigDecimal("0.01"),j.queryForObject("SELECT pool_balance FROM dividend_pool",BigDecimal.class));
  assertEquals(0,tx.execute(s->dividends.distributeDaily(LocalDate.of(2026,10,15))).intValue());
 }
 @Test void creditFailureRollsBackAll(){
  j.update("INSERT INTO ums_earnings_ledger(member_id,idempotency_key) VALUES(1,'dividend:1:2026-10-15')");
  assertThrows(IllegalStateException.class,()->tx.execute(s->dividends.distributeDaily(LocalDate.of(2026,10,15))));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM member_dividend_ledger",Integer.class));
  assertEquals(new BigDecimal("0.05"),j.queryForObject("SELECT pool_balance FROM dividend_pool",BigDecimal.class));
 }
}
