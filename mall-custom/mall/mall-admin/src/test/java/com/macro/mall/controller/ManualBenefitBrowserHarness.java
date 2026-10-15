package com.macro.mall.controller;
import com.macro.mall.common.api.CommonResult;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** Loopback-only, test-classpath harness. Uses the actual benefit controller/transactions; JWT is supplied by the test header filter. */
public class ManualBenefitBrowserHarness {
 @Configuration @Profile("manual-benefit-test") @Import({ManualBenefitHttpTest.Application.class,FinanceFixture.class}) static class Config {}
 @RestController @Profile("manual-benefit-test") static class FinanceFixture {
  private final JdbcTemplate j; FinanceFixture(JdbcTemplate j){this.j=j;}
  @GetMapping("/p0/finance") CommonResult<?> finance(){return CommonResult.success(Map.of("accounts",j.queryForList("SELECT * FROM ums_earnings"),"ledger",j.queryForList("SELECT * FROM ums_earnings_ledger"),"withdraws",List.of()));}
 }
 public static void main(String[] args) throws Exception {
  var app=new SpringApplication(Config.class);app.setAdditionalProfiles("manual-benefit-test");
  app.setDefaultProperties(Map.of("server.address","127.0.0.1","server.port","18080","spring.autoconfigure.exclude","com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure","spring.main.banner-mode","off"));
  var context=app.run(args);var fixtures=new ManualBenefitHttpTest();fixtures.j=context.getBean(JdbcTemplate.class);fixtures.setup();
  context.getBean(JdbcTemplate.class).execute("CREATE TABLE ums_member_contact(member_id BIGINT PRIMARY KEY,real_name VARCHAR(64),email VARCHAR(128),wechat_account VARCHAR(64))");
  org.h2.tools.Server.createTcpServer("-tcpPort","19092","-ifExists").start();
  System.out.println("ISOLATED_UI_FIXTURE_READY");
 }
}
