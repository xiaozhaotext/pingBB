package com.macro.mall.portal.service;
import com.macro.mall.portal.config.RechargeProperties;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class RechargeBalanceIntegrityTest {
 JdbcTemplate j;RechargeService service;TransactionTemplate tx;
 @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:h2:mem:balanceaudit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");j=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));j.execute("DROP ALL OBJECTS");j.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT)");j.update("INSERT INTO ums_recharge_account VALUES(1,10000),(2,10000)");j.execute("CREATE TABLE ums_recharge_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(32),amount_cents BIGINT,balance_after_cents BIGINT,biz_id VARCHAR(64),idempotency_key VARCHAR(64) UNIQUE,remark VARCHAR(255))");service=new RechargeService(j,new RechargeProperties());}
 @Test void requestReplayCannotChangeMemberAmountOrBusinessOrder(){
  tx.execute(s->service.spendBalance(1,1000,"1","balance-test-001","test"));
  assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.spendBalance(2,1000,"1","balance-test-001","test")));
  assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.spendBalance(1,2000,"1","balance-test-001","test")));
  assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.spendBalance(1,1000,"2","balance-test-001","test")));
 }
 @Test void eightConcurrentRetriesDebitOnlyOnceAndReturnTheOriginalResult() throws Exception {
  var pool=Executors.newFixedThreadPool(8);try{var start=new CountDownLatch(1);var tasks=new java.util.ArrayList<Future<Long>>();for(int i=0;i<8;i++)tasks.add(pool.submit(()->{start.await();return tx.execute(s->service.spendBalance(1,1000,"1","balance-test-001","test"));}));start.countDown();for(var f:tasks)assertEquals(9000L,f.get(20,TimeUnit.SECONDS));assertEquals(9000L,service.balanceCents(1));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_recharge_ledger",Integer.class));}finally{pool.shutdownNow();}
 }
 @Test void refundReplayCannotCrossMemberOrExceedOriginalPayment(){
  tx.execute(s->service.spendBalance(1,1000,"1","balance-test-001","test"));
  tx.executeWithoutResult(s->service.refundBalance(1,1000,"1","balance-refund-001","test"));
  tx.executeWithoutResult(s->service.refundBalance(1,1000,"1","balance-refund-001","test"));
  assertEquals(10000L,service.balanceCents(1));
  assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->service.refundBalance(2,1000,"1","balance-refund-001","test")));
  assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->service.refundBalance(1,1000,"1","balance-refund-002","test")));
 }
}
