package com.macro.mall.controller;
import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
/** Embedded real HTTP + real SQL/transactions. Test header supplies principal; production JWT/RBAC not simulated as a pass. */
@SpringBootTest(classes=ManualBenefitHttpTest.Application.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.main.banner-mode=off","logging.level.root=WARN","spring.autoconfigure.exclude=com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure"})
@org.springframework.test.context.ActiveProfiles("manual-benefit-test")
class ManualBenefitHttpTest {
 @Configuration @Profile("manual-benefit-test") @EnableTransactionManagement
 @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class,ManagementWebSecurityAutoConfiguration.class})
 @Import({ManualBenefitController.class,P0AdminAccess.class})
 static class Application {
  @Bean DataSource ds(){return new DriverManagerDataSource("jdbc:h2:mem:benefithttp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
  @Bean FilterRegistrationBean<jakarta.servlet.Filter> testPrincipal(){var bean=new FilterRegistrationBean<jakarta.servlet.Filter>();bean.setFilter((req,res,chain)->chain.doFilter(new jakarta.servlet.http.HttpServletRequestWrapper((jakarta.servlet.http.HttpServletRequest)req){@Override public java.security.Principal getUserPrincipal(){return "yes".equals(getHeader("X-Test-Admin"))?()->"testadmin":null;}},res));return bean;}
 }
 @Autowired TestRestTemplate http; @Autowired JdbcTemplate j;
 @BeforeEach void setup(){
  j.execute("DROP ALL OBJECTS");
  j.execute("CREATE TABLE sys_audit_log(operator_id BIGINT,operator_name VARCHAR(64),module VARCHAR(64),action VARCHAR(64),target_id BIGINT,detail VARCHAR(2000))");
  j.execute("CREATE TABLE ums_admin(id BIGINT,username VARCHAR(50),status INT)");j.update("INSERT INTO ums_admin VALUES(3,'testadmin',1)");
  j.execute("CREATE TABLE ums_admin_role_relation(admin_id BIGINT,role_id BIGINT)");j.update("INSERT INTO ums_admin_role_relation VALUES(3,5)");
  j.execute("CREATE TABLE ums_member_identity(member_id BIGINT PRIMARY KEY,identity_type INT,status INT)");j.update("INSERT INTO ums_member_identity VALUES(1,2,1),(2,1,1)");
  j.execute("CREATE TABLE member_manual_benefit(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,benefit_type VARCHAR(24),benefit_period VARCHAR(7),amount DECIMAL(18,2),status VARCHAR(16) DEFAULT 'PENDING',request_reason VARCHAR(500),requested_by BIGINT,reviewed_by BIGINT,review_note VARCHAR(500),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,reviewed_at TIMESTAMP,UNIQUE(member_id,benefit_type,benefit_period))");
  j.execute("CREATE TABLE ums_earnings(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT UNIQUE,total_earnings DECIMAL(18,2),available_earnings DECIMAL(18,2),frozen_earnings DECIMAL(18,2),withdrawn DECIMAL(18,2),version BIGINT)");
  j.execute("CREATE TABLE ums_earnings_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(50),amount DECIMAL(18,2),balance_after DECIMAL(18,2),biz_id VARCHAR(100),idempotency_key VARCHAR(100) UNIQUE,remark VARCHAR(1000))");
 }
 Map post(String path,Map body){var h=new org.springframework.http.HttpHeaders();h.set("X-Test-Admin","yes");return http.postForObject("/p0/benefits"+path,new org.springframework.http.HttpEntity<>(body,h),Map.class);}
 Map request(String type,long member){return post("",Map.of("memberId",member,"type",type,"period","2026-10","reason","经人工核实符合申请条件"));}
 @Test void monthlyReviewCreditsExactlyTwoHundredOnce(){
  assertEquals(200,request("MONTHLY_REWARD",1).get("code"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM ums_earnings",Integer.class));
  assertNotEquals(200,request("MONTHLY_REWARD",1).get("code"));
  var review=Map.of("approve",true,"note","核对会员资格与本月发放记录完成");
  assertEquals(200,post("/1/review",review).get("code"));assertNotEquals(200,post("/1/review",review).get("code"));
  assertEquals(new BigDecimal("200.00"),j.queryForObject("SELECT available_earnings FROM ums_earnings WHERE member_id=1",BigDecimal.class));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_earnings_ledger",Integer.class));
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class));
 }
 @Test void travelRedeemsOnceWithoutCreditingCash(){
  assertEquals(200,request("TRAVEL",2).get("code"));assertEquals(200,post("/1/review",Map.of("approve",true,"note","旅游服务已完成，凭据TRAVEL-001")).get("code"));
  assertNotEquals(200,request("TRAVEL",2).get("code"));assertEquals("REDEEMED",j.queryForObject("SELECT status FROM member_manual_benefit",String.class));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM ums_earnings",Integer.class));
 }
 @Test void eligibilityAndUnauthenticatedRequestsAreRejected(){
  assertNotEquals(200,request("MONTHLY_REWARD",2).get("code"));
  assertEquals(401,http.getForEntity("/p0/benefits",String.class).getStatusCode().value());
 }
 @Test void ledgerFailureRollsBackBalanceAndReview(){
  request("MONTHLY_REWARD",1);j.execute("DROP TABLE ums_earnings_ledger");
  // Server reports failure; transaction must leave request pending and no credited account.
  try {post("/1/review",Map.of("approve",true,"note","测试账本不可用时事务回滚"));}catch(org.springframework.web.client.HttpServerErrorException expected){}
  assertEquals("PENDING",j.queryForObject("SELECT status FROM member_manual_benefit",String.class));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM ums_earnings",Integer.class));
 }
}
