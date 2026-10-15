package com.macro.mall.portal.service;
import com.macro.mall.model.UmsMember;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.controller.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.util.*;
import static org.mockito.Mockito.*;
/** Test-only member principal, real controllers and SQL; not a production authentication test. */
public class MemberBrowserHarness {
 @Configuration @Profile("member-browser-test")
 @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class,ManagementWebSecurityAutoConfiguration.class})
 @Import({MemberBenefitController.class,MemberContactController.class,Info.class})
 static class Config {
  @Bean DataSource ds(){return new DriverManagerDataSource("jdbc:h2:tcp://127.0.0.1:19092/mem:benefithttp;MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","");}
  @Bean UmsMemberService member(){var service=mock(UmsMemberService.class);var m=new UmsMember();m.setId(1L);m.setUsername("qa-member");when(service.getCurrentMember()).thenReturn(m);return service;}
 }
 @RestController @Profile("member-browser-test") static class Info {
  @GetMapping("/sso/info") CommonResult<?> info(){return CommonResult.success(Map.of("id",1,"username","qa-member"));}
 }
 public static void main(String[] args){var app=new SpringApplication(Config.class);app.setAdditionalProfiles("member-browser-test");app.run(args);}
}
