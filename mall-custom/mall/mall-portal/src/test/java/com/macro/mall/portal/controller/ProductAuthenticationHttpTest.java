package com.macro.mall.portal.controller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.userdetails.*;
import com.macro.mall.security.config.*;
import com.macro.mall.security.component.*;
import com.macro.mall.security.util.JwtTokenUtil;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=ProductAuthenticationHttpTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"jwt.secret=qa-private-signing-secret-012345678901234567890123456789","jwt.expiration=600","jwt.tokenHeader=Authorization","jwt.tokenHead=Bearer ","spring.autoconfigure.exclude=com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure","logging.level.root=WARN"})
@org.springframework.test.context.ActiveProfiles("product-auth-test")
class ProductAuthenticationHttpTest {
 @Configuration @Profile("product-auth-test") @EnableAutoConfiguration @Import({SecurityConfig.class,Endpoints.class})
 static class App {
  @Bean IgnoreUrlsConfig urls(){var c=new IgnoreUrlsConfig();c.setUrls(List.of("/product/**"));return c;}
  @Bean JwtAuthenticationTokenFilter jwtFilter(){return new JwtAuthenticationTokenFilter();}
  @Bean JwtTokenUtil tokens(){return new JwtTokenUtil();}
  @Bean RestAuthenticationEntryPoint entry(){return new RestAuthenticationEntryPoint();}
  @Bean RestfulAccessDeniedHandler denied(){return new RestfulAccessDeniedHandler();}
  @Bean UserDetailsService users(){return name->User.withUsername(name).password("unused").authorities("USER").build();}
 }
 @RestController @Profile("product-auth-test") static class Endpoints {
  @GetMapping({"/product/comment/pending","/product/comment/list"}) Map<String,Object> get(){return Map.of("code",200);}
  @PostMapping("/product/comment/create") Map<String,Object> create(){return Map.of("code",200);}
 }
 @Autowired TestRestTemplate http;@Autowired JwtTokenUtil tokens;@Autowired UserDetailsService users;
 @Test void legacyWildcardCannotMakePrivateCommentActionsPublic(){
  assertEquals(401,http.getForEntity("/product/comment/pending",Map.class).getStatusCode().value());
  assertEquals(401,http.postForEntity("/product/comment/create",Map.of(),Map.class).getStatusCode().value());
  assertEquals(200,http.getForEntity("/product/comment/list",Map.class).getStatusCode().value());
 }
 @Test void actualSignedJwtAuthenticatesPrivateCommentRead(){
  var h=new org.springframework.http.HttpHeaders();h.setBearerAuth(tokens.generateToken(users.loadUserByUsername("qa")));
  assertEquals(200,http.exchange("/product/comment/pending",org.springframework.http.HttpMethod.GET,new org.springframework.http.HttpEntity<>(h),Map.class).getStatusCode().value());
 }
}
