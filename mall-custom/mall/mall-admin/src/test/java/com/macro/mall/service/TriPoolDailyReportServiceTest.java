package com.macro.mall.service;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TriPoolDailyReportServiceTest {
 JdbcTemplate jdbc;TriPoolDailyReportService reports;
 LocalDate date=LocalDate.of(2026,10,16);
 @BeforeEach void setup(){
  jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:daily_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE tri_pool_ledger(pool_code VARCHAR(16),entry_type VARCHAR(16),amount DECIMAL(18,2),created_at TIMESTAMP)");
  jdbc.execute("CREATE TABLE tri_pool_distribution(pool_code VARCHAR(16),member_id BIGINT,created_at TIMESTAMP)");
  jdbc.execute("CREATE TABLE tri_pool_batch(pool_code VARCHAR(16),paid_total DECIMAL(18,2),created_at TIMESTAMP)");
  reports=new TriPoolDailyReportService(jdbc);
 }
 Timestamp at(String text){return Timestamp.from(OffsetDateTime.parse(text).toInstant());}
 void fund(String time,String type,String amount){jdbc.update("INSERT INTO tri_pool_ledger VALUES('PARTNER',?,?,?)",type,new BigDecimal(amount),at(time));}
 @SuppressWarnings("unchecked") Map<String,Object> partner(LocalDate day){return ((List<Map<String,Object>>)reports.daily(day).get("pools")).get(0);}
 void money(String expected,Object actual){assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));}
 @Test void historicalOpeningAndClosingExcludeNextDayAndUseSignedLedger(){
  fund("2026-10-15T23:59:59+08:00","FUND","100");
  fund("2026-10-16T00:00:00+08:00","FUND","500");
  fund("2026-10-16T12:00:00+08:00","DISTRIBUTE","-120");
  fund("2026-10-16T23:59:59+08:00","DISTRIBUTE","-30");
  fund("2026-10-17T00:00:00+08:00","FUND","900");
  var row=partner(date);money("100",row.get("opening"));money("500",row.get("funded"));money("150",row.get("distributed"));money("450",row.get("closing"));
  assertEquals(3L,((Number)row.get("ledgerCount")).longValue());
 }
 @Test void emptyDayCarriesHistoricalBalanceWithoutInventingAwards(){
  fund("2026-10-15T12:00:00+08:00","FUND","200");
  var row=partner(date);money("200",row.get("opening"));money("200",row.get("closing"));money("0",row.get("funded"));money("0",row.get("distributed"));
  assertEquals(0L,((Number)row.get("awardCount")).longValue());
 }
 @Test void distinctRecipientsAndPaidBatchesAreSeparateFromAwardCount(){
  for(long member:new long[]{1,1,2})jdbc.update("INSERT INTO tri_pool_distribution VALUES('PARTNER',?,?)",member,at("2026-10-16T10:00:00+08:00"));
  jdbc.update("INSERT INTO tri_pool_distribution VALUES('PARTNER',3,?)",at("2026-10-17T00:00:00+08:00"));
  for(int amount:new int[]{0,10,20})jdbc.update("INSERT INTO tri_pool_batch VALUES('PARTNER',?,?)",amount,at("2026-10-16T10:00:00+08:00"));
  var row=partner(date);assertEquals(3L,((Number)row.get("awardCount")).longValue());assertEquals(2L,((Number)row.get("recipientCount")).longValue());assertEquals(2L,((Number)row.get("batchCount")).longValue());
 }
 @Test void includesAllThreePoolsWhenTheyHaveNoActivity(){
  var report=reports.daily(date);assertEquals("2026-10-16",report.get("date"));assertEquals("Asia/Shanghai",report.get("timezone"));
  assertEquals(3,((List<?>)report.get("pools")).size());money("0",partner(date).get("closing"));
 }
 @Test void dayBoundsAreExplicitBeijingMidnightInstants(){
  assertEquals(Instant.parse("2026-10-15T16:00:00Z"),reports.start(date).toInstant());
  assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")),reports.day(null));
 }
}
