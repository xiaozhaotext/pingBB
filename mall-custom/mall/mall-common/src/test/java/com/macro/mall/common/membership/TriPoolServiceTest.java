package com.macro.mall.common.membership;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses an isolated in-memory H2 database only; never loads deployment configuration. */
class TriPoolServiceTest {
    JdbcTemplate jdbc; TransactionTemplate tx; TriPoolService service;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:tripool_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        String sql=Files.readString(Path.of(System.getProperty("basedir","."),"../document/sql/custom-v10/50_three_dividend_pools.sql"));
        sql=sql.substring(0,sql.indexOf("-- Menu/resource additions"));
        sql=sql.replaceAll("(?m)^--.*$","");
        for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
        jdbc.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY,username VARCHAR(64),integration INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE pms_product(id BIGINT PRIMARY KEY,name VARCHAR(64),price DECIMAL(18,2),publish_status INT,delete_status INT)");
        jdbc.execute("CREATE TABLE ums_earnings(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT UNIQUE,total_earnings DECIMAL(18,2),available_earnings DECIMAL(18,2),frozen_earnings DECIMAL(18,2),withdrawn DECIMAL(18,2),version BIGINT)");
        jdbc.execute("CREATE TABLE ums_earnings_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(50),amount DECIMAL(18,2),balance_after DECIMAL(18,2),biz_id VARCHAR(100),idempotency_key VARCHAR(100) UNIQUE,remark VARCHAR(255))");
        jdbc.execute("CREATE TABLE sys_audit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,operator_name VARCHAR(100),operator_id BIGINT,module VARCHAR(50),action VARCHAR(100),target_id BIGINT,detail VARCHAR(2000))");
        jdbc.execute("CREATE TABLE test_points(member_id BIGINT,wallet_type INT,points BIGINT,idempotency_key VARCHAR(100) UNIQUE)");
        for(int i=1;i<=20;i++)jdbc.update("INSERT INTO ums_member(id,username) VALUES(?,?)",i,"test"+i);
        service=new TriPoolService(jdbc);ReflectionTestUtils.setField(service,"enabled",true);
    }
    boolean buy(long buyer,Long parent) {return tx.execute(s->service.acceptPayment("ORDER:"+buyer,buyer,parent,TriPoolRules.PRICE,
            (member,type,points,key)->jdbc.update("INSERT INTO test_points VALUES(?,?,?,?)",member,type,points,key)));}
    BigDecimal amount(String sql,Object...args){return jdbc.queryForObject(sql,BigDecimal.class,args);}
    void eq(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    LocalDate yesterday(){return LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);}
    void backdateFunds(){jdbc.update("UPDATE tri_pool_ledger SET created_at=? WHERE entry_type='FUND'",java.sql.Timestamp.from(yesterday().atTime(12,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant()));}

    @Test void standaloneMembershipReceiptIsAcceptedAndIdempotent() {
        var post=(TriPoolService.PointsCredit)(member,type,points,key)->jdbc.update("INSERT INTO test_points VALUES(?,?,?,?)",member,type,points,key);
        assertEquals(Boolean.TRUE,tx.execute(s->service.acceptPayment("MEMBERSHIP:99",1,null,TriPoolRules.PRICE,post)));
        assertEquals(Boolean.FALSE,tx.execute(s->service.acceptPayment("MEMBERSHIP:99",1,null,TriPoolRules.PRICE,post)));
        eq("500",amount("SELECT total_in FROM tri_pool WHERE code='PARTNER'"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_points",Integer.class));
    }
    @Test void registrationIsVisitorAndDoesNotFundOrRewardAnything() {
        var info=service.memberSummary(1);
        assertEquals("VISITOR",info.get("tier"));assertEquals(false,info.get("customTravelEligible"));
        eq("0",amount("SELECT SUM(balance) FROM tri_pool"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ums_earnings_ledger",Integer.class));
    }
    @Test void paidMemberFundsAllPoolsAndDuplicateCallbackDoesNothing() {
        assertTrue(buy(1,null));assertFalse(buy(1,null));
        eq("500",amount("SELECT total_in FROM tri_pool WHERE code='PARTNER'"));
        eq("200",amount("SELECT balance FROM tri_pool WHERE code='COFOUNDER'"));
        eq("200",amount("SELECT balance FROM tri_pool WHERE code='OPERATIONS'"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_points",Integer.class));
        assertEquals(7998L,jdbc.queryForObject("SELECT SUM(points) FROM test_points",Long.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tri_payment_event",Integer.class));
        assertThrows(IllegalStateException.class,()->tx.execute(s->service.acceptPayment("ORDER:1",2,null,TriPoolRules.PRICE,(m,t,p,k)->{})));
    }
    @Test void referralsUpgradeOnlyTheirDirectPaidParentAndUseSharedCap() {
        buy(1,null);
        for(long id=2;id<=10;id++) {
            buy(id,1L);
            if(id==4) {
                assertEquals("PARTNER",jdbc.queryForObject("SELECT tier FROM tri_member WHERE member_id=1",String.class));
                assertEquals(3L,jdbc.queryForObject("SELECT direct_paid_count FROM tri_member WHERE member_id=1",Long.class));
            }
        }
        assertEquals("COFOUNDER",jdbc.queryForObject("SELECT tier FROM tri_member WHERE member_id=1",String.class));
        assertEquals("MEMBER",jdbc.queryForObject("SELECT tier FROM tri_member WHERE member_id=2",String.class));
        backdateFunds();tx.execute(s->service.distributeDaily(yesterday()));
        eq("3599.10",amount("SELECT SUM(amount) FROM ums_earnings_ledger WHERE member_id=1 AND change_type='referral_reward'"));
        eq("1200",amount("SELECT SUM(amount) FROM tri_pool_distribution WHERE member_id=1 AND pool_code='COFOUNDER'"));
        eq("800",amount("SELECT balance FROM tri_pool WHERE code='COFOUNDER'"));
        assertTrue(amount("SELECT MAX(cap_amount) FROM tri_pool_distribution WHERE member_id=1 AND pool_code='COFOUNDER'").compareTo(new BigDecimal("1200.00"))<=0);
        eq("2000",amount("SELECT balance FROM tri_pool WHERE code='OPERATIONS'"));
        assertEquals(10800L,jdbc.queryForObject("SELECT SUM(points) FROM test_points WHERE member_id=1 AND idempotency_key LIKE '%referrer%'",Long.class));
        assertTrue(jdbc.queryForObject("SELECT MAX(received_before+amount) FROM tri_pool_distribution WHERE member_id=1 AND pool_code='PARTNER'",BigDecimal.class).compareTo(TriPoolRules.partnerCap(9))<=0);
        var summary=service.memberSummary(1);
        eq("24000",(BigDecimal)summary.get("totalCap"));
        assertEquals(0,((BigDecimal)summary.get("totalCap")).subtract((BigDecimal)summary.get("totalReceived"))
                .subtract((BigDecimal)summary.get("totalRemaining")).compareTo(BigDecimal.ZERO));
    }
    @Test void cofounderTenReferralsSharesOneRemainingBalanceAcrossPools() {
        buy(1,null);
        jdbc.update("UPDATE tri_member SET tier='COFOUNDER',direct_paid_count=10 WHERE member_id=1");
        jdbc.update("INSERT INTO tri_pool_position(member_id,pool_code,received) VALUES(1,'PARTNER',2439.39)");
        jdbc.update("INSERT INTO tri_pool_position(member_id,pool_code,received) VALUES(1,'COFOUNDER',1200.00)");
        var summary=service.memberSummary(1);
        eq("25200",(BigDecimal)summary.get("totalCap"));
        eq("3639.39",(BigDecimal)summary.get("totalReceived"));
        eq("21560.61",(BigDecimal)summary.get("totalRemaining"));
        @SuppressWarnings("unchecked") var pools=(List<Map<String,Object>>)summary.get("pools");
        eq("24000",(BigDecimal)pools.get(0).get("cap"));
        eq("21560.61",(BigDecimal)pools.get(0).get("remaining"));
        eq("1200",(BigDecimal)pools.get(1).get("cap"));
        eq("0",(BigDecimal)pools.get(1).get("remaining"));
    }
    @Test void dailyDistributionNeverPaysBeyondBothPoolsSharedCap() {
        buy(1,null);
        jdbc.update("UPDATE tri_member SET tier='COFOUNDER',direct_paid_count=10 WHERE member_id=1");
        jdbc.update("INSERT INTO tri_pool_position(member_id,pool_code,received) VALUES(1,'PARTNER',23999.00)");
        jdbc.update("INSERT INTO tri_pool_position(member_id,pool_code,received) VALUES(1,'COFOUNDER',1200.00)");
        backdateFunds();
        tx.execute(s->service.distributeDaily(yesterday()));
        eq("1",amount("SELECT SUM(amount) FROM tri_pool_distribution WHERE member_id=1 AND pool_code='PARTNER'"));
        eq("24000",amount("SELECT received FROM tri_pool_position WHERE member_id=1 AND pool_code='PARTNER'"));
        eq("1200",amount("SELECT received FROM tri_pool_position WHERE member_id=1 AND pool_code='COFOUNDER'"));
        eq("0",(BigDecimal)service.memberSummary(1).get("totalRemaining"));
    }
    @Test void pointsFailureRollsBackMembershipFundingAndAwards() {
        assertThrows(IllegalStateException.class,()->tx.execute(s->service.acceptPayment("ORDER:1",1,null,TriPoolRules.PRICE,(m,t,p,k)->{throw new IllegalStateException("wallet unavailable");})));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tri_member",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tri_payment_event",Integer.class));
        eq("0",amount("SELECT SUM(total_in) FROM tri_pool"));
    }
    @Test void operationsRequiresManualPreviewAndRetriesDoNotDoublePay() {
        buy(1,null);buy(2,null);
        tx.execute(s->{service.setOperationsMember(3,2,true,99,"admin","指定运营人员份额");return null;});
        tx.execute(s->{service.setOperationsMember(4,1,true,99,"admin","指定运营人员份额");return null;});
        var preview=service.operationsPreview();String token=(String)preview.get("previewToken");
        String key="operations_request_0001";
        tx.execute(s->service.distributeOperations(key,new BigDecimal("400"),3,token,99,"admin"));
        tx.execute(s->service.distributeOperations(key,new BigDecimal("400"),3,token,99,"admin"));
        eq("266.66",amount("SELECT amount FROM tri_pool_distribution WHERE member_id=3 AND pool_code='OPERATIONS'"));
        eq("133.33",amount("SELECT amount FROM tri_pool_distribution WHERE member_id=4 AND pool_code='OPERATIONS'"));
        eq("0.01",amount("SELECT balance FROM tri_pool WHERE code='OPERATIONS'"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tri_pool_batch WHERE pool_code='OPERATIONS'",Integer.class));
    }
    @Test void changingRecipientWithSameShareCountInvalidatesPreview() {
        buy(1,null);
        tx.execute(s->{service.setOperationsMember(2,1,true,99,"admin","指定运营人员份额");return null;});
        String token=(String)service.operationsPreview().get("previewToken");
        tx.execute(s->{service.setOperationsMember(2,1,false,99,"admin","停用原运营人员份额");service.setOperationsMember(3,1,true,99,"admin","指定新运营人员份额");return null;});
        assertThrows(IllegalStateException.class,()->tx.execute(s->service.distributeOperations("operations_request_0002",new BigDecimal("200"),1,token,99,"admin")));
        eq("200",amount("SELECT balance FROM tri_pool WHERE code='OPERATIONS'"));
    }
    @Test void visitorReferrerGetsNothingAndWrongPriceIsRejected() {
        assertTrue(buy(2,1L));
        assertNull(jdbc.queryForObject("SELECT referrer_id FROM tri_payment_event WHERE member_id=2",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ums_earnings_ledger WHERE member_id=1",Integer.class));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.acceptPayment("ORDER:3",3,null,new BigDecimal("3998"),(m,t,p,k)->{})));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tri_payment_event",Integer.class));
    }
    @Test void concurrentDuplicatePaymentHasOneGrantAndOneReferral() throws Exception {
        buy(1,null);
        var executor=Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start=new CountDownLatch(1);
            List<Future<Boolean>> calls=new ArrayList<>();
            for(int i=0;i<4;i++)calls.add(executor.submit(()->{start.await();return buy(2,1L);}));
            start.countDown();int accepted=0;
            for(var result:calls)if(result.get(15,TimeUnit.SECONDS))accepted++;
            assertEquals(1,accepted);
            assertEquals(1L,jdbc.queryForObject("SELECT direct_paid_count FROM tri_member WHERE member_id=1",Long.class));
            eq("399.90",amount("SELECT SUM(amount) FROM ums_earnings_ledger WHERE change_type='referral_reward'"));
            eq("0",amount("SELECT SUM(total_in)-SUM(total_out)-SUM(balance) FROM tri_pool"));
        } finally { executor.shutdownNow(); }
    }
    @Test void earningsFailureRollsBackReferralAndPaymentInsteadOfPartiallyGranting() {
        buy(1,null);
        jdbc.update("INSERT INTO ums_earnings_ledger(member_id,idempotency_key) VALUES(1,'tri:ORDER:2:referral')");
        assertThrows(IllegalStateException.class,()->buy(2,1L));
        assertFalse(service.isMember(2));
        assertEquals(0L,jdbc.queryForObject("SELECT direct_paid_count FROM tri_member WHERE member_id=1",Long.class));
        eq("500",amount("SELECT total_in FROM tri_pool WHERE code='PARTNER'"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_points WHERE member_id=2",Integer.class));
    }

    @Test void paymentOnlyFundsAndMidnightPaysAllThreePoolsOnce() {
        buy(1,null);buy(2,1L);
        tx.execute(s->{service.setOperationsMember(3,1,true,99,"admin","运营份额测试依据");return null;});
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tri_pool_batch",Integer.class));
        eq("399.90",amount("SELECT available_earnings FROM ums_earnings WHERE member_id=1"));
        backdateFunds();
        tx.execute(s->service.distributeDaily(yesterday()));
        tx.execute(s->service.distributeDaily(yesterday()));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM tri_pool_batch",Integer.class));
        eq("999.99",amount("SELECT SUM(amount) FROM tri_pool_distribution WHERE pool_code='PARTNER'"));
        eq("400",amount("SELECT available_earnings FROM ums_earnings WHERE member_id=3"));
        eq("400",amount("SELECT balance FROM tri_pool WHERE code='COFOUNDER'"));
        eq("0",amount("SELECT SUM(total_in-total_out-balance) FROM tri_pool"));
    }

    @Test void delayedSettlementExcludesTodaysFundingAndKeepsNoRecipientMoney() {
        buy(1,null);backdateFunds();buy(2,null);
        tx.execute(s->service.distributeDaily(yesterday()));
        eq("500",amount("SELECT SUM(amount) FROM tri_pool_distribution WHERE pool_code='PARTNER'"));
        eq("500",amount("SELECT balance FROM tri_pool WHERE code='PARTNER'"));
        eq("400",amount("SELECT balance FROM tri_pool WHERE code='OPERATIONS'"));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.distributeDaily(yesterday().plusDays(1))));
    }

    @Test void concurrentDailyWorkersCannotPayTwice() throws Exception {
        buy(1,null);backdateFunds();
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->tx.execute(s->service.distributeDaily(yesterday())));
            var second=executor.submit(()->tx.execute(s->service.distributeDaily(yesterday())));
            first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM tri_pool_batch",Integer.class));
            eq("500",amount("SELECT SUM(amount) FROM tri_pool_distribution"));
        } finally {executor.shutdownNow();}
    }

    @Test void dailyFailureRollsBackAllPoolsAndCanRetry() {
        buy(1,null);backdateFunds();
        jdbc.execute("ALTER TABLE ums_earnings_ledger ADD CONSTRAINT reject_dividend CHECK(change_type<>'dividend')");
        assertThrows(Exception.class,()->tx.execute(s->service.distributeDaily(yesterday())));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tri_pool_batch",Integer.class));
        eq("500",amount("SELECT balance FROM tri_pool WHERE code='PARTNER'"));
        jdbc.execute("ALTER TABLE ums_earnings_ledger DROP CONSTRAINT reject_dividend");
        tx.execute(s->service.distributeDaily(yesterday()));
        eq("500",amount("SELECT SUM(amount) FROM tri_pool_distribution"));
    }
    @Test void memberSummaryUsesStepCapAndKeepsHistoricalReceipts() {
        buy(1,null);
        jdbc.update("UPDATE tri_member SET direct_paid_count=9,tier='COFOUNDER' WHERE member_id=1");
        jdbc.update("INSERT INTO tri_pool_position(member_id,pool_code,received) VALUES(1,'PARTNER',23999)");
        var summary=service.memberSummary(1);
        @SuppressWarnings("unchecked") var pools=(List<Map<String,Object>>)summary.get("pools");
        var partner=pools.stream().filter(p->p.get("code").equals("PARTNER")).findFirst().orElseThrow();
        var cofounder=pools.stream().filter(p->p.get("code").equals("COFOUNDER")).findFirst().orElseThrow();
        eq("24000",(BigDecimal)partner.get("cap"));eq("23999",(BigDecimal)partner.get("received"));eq("1",(BigDecimal)partner.get("remaining"));
        eq("24000",(BigDecimal)cofounder.get("cap"));
    }
}
