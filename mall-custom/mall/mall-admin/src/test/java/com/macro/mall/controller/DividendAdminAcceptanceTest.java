package com.macro.mall.controller;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.P0ReferralAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.security.Principal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DividendAdminAcceptanceTest {
 @Test void poolAndDailyStatisticsReadActualRows() {
  var j=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:dividendadmin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
  j.execute("DROP ALL OBJECTS");
  j.execute("CREATE TABLE dividend_contribution_event(id INT,stat_date DATE,member_id BIGINT,event_type VARCHAR,biz_id VARCHAR,contribution_value DECIMAL,verify_status INT,create_time TIMESTAMP)");
  j.execute("CREATE TABLE dividend_snapshot(id INT,stat_date DATE,member_id BIGINT,contribution_value DECIMAL,pool_total DECIMAL,ratio DECIMAL,estimated_amount DECIMAL,calc_batch VARCHAR,simulate_flag INT)");
  j.execute("CREATE TABLE dividend_exception_audit(id INT,stat_date DATE,member_id BIGINT,reason VARCHAR,detail VARCHAR,create_time TIMESTAMP)");
  j.execute("CREATE TABLE dividend_pool(id INT,pool_balance DECIMAL(14,2),total_in DECIMAL(14,2),total_out DECIMAL(14,2),update_time TIMESTAMP)");
  j.update("INSERT INTO dividend_pool VALUES(1,600,1000,400,CURRENT_TIMESTAMP)");
  j.execute("CREATE TABLE dividend_pool_ledger(change_amount DECIMAL(14,2),create_time TIMESTAMP)");
  j.update("INSERT INTO dividend_pool_ledger VALUES(1000,CURRENT_TIMESTAMP),(-400,CURRENT_TIMESTAMP)");
  j.execute("CREATE TABLE member_dividend_ledger(stat_date DATE,member_id BIGINT,shares INT,dividend_amount DECIMAL(14,2),calc_batch VARCHAR)");
  j.update("INSERT INTO member_dividend_ledger VALUES(CURDATE(),1,1,100,'today'),(CURDATE(),2,3,300,'today')");
  var access=mock(P0AdminAccess.class);Principal p=()->"test-admin";var c=new P0AdminController(j,access,mock(P0ReferralAdminService.class));
  var data=c.dividend(p).getData();verify(access).requireAuthorized(p);
  assertEquals(new BigDecimal("600.00"),((Map<?,?>)data.get("poolStats")).get("pool_balance"));
  assertEquals(new BigDecimal("1000.00"),((Map<?,?>)data.get("poolTodayIn")).get("today_in"));
  var daily=(Map<?,?>)((List<?>)data.get("dailyDetails")).get(0);
  assertEquals(4,((Number)daily.get("total_shares")).intValue());assertEquals(2,((Number)daily.get("recipients")).intValue());
  assertEquals(new BigDecimal("100.00"),daily.get("per_share_amount"));
 }
 @Test void unauthorizedReadStopsBeforeDatabaseAccess() {
  var access=mock(P0AdminAccess.class);var j=mock(JdbcTemplate.class);Principal p=()->"unauthorized";
  when(access.requireAuthorized(p)).thenThrow(new IllegalStateException("denied"));
  assertThrows(IllegalStateException.class,()->new P0AdminController(j,access,mock(P0ReferralAdminService.class)).dividend(p));verifyNoInteractions(j);
 }
}
