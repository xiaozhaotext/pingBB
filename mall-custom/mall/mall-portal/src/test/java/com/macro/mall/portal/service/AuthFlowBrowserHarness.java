package com.macro.mall.portal.service;

import com.macro.mall.common.exception.GlobalExceptionHandler;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.mapper.*;
import com.macro.mall.portal.component.MessageRecorder;
import com.macro.mall.portal.config.MembershipPaymentProperties;
import com.macro.mall.portal.controller.*;
import com.macro.mall.portal.dao.*;
import com.macro.mall.portal.service.impl.*;
import com.macro.mall.security.component.*;
import com.macro.mall.security.config.*;
import com.macro.mall.security.util.JwtTokenUtil;
import org.mybatis.spring.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;

/** Local-only browser QA: real registration, BCrypt, JWT, membership, points and pool transactions.
 * Redis OTP cache and SMS are in-memory/test adapters. No external SMS or payments. */
public class AuthFlowBrowserHarness {
    @Configuration @Profile("auth-browser-test") @EnableAutoConfiguration
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({SecurityConfig.class,UmsMemberController.class,UmsMemberServiceImpl.class,MessageRecorder.class,
        MembershipPaymentController.class,MembershipPurchaseOrderService.class,MembershipVerifiedPaymentHandler.class,
        TriMembershipPurchaseService.class,TriPoolService.class,TriPoolMemberController.class,
        LocalMockMembershipPaymentController.class,LocalMockMembershipPaymentService.class,
        PointsWalletServiceImpl.class,GlobalExceptionHandler.class})
    static class App {
        @Bean DataSource dataSource() throws Exception {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:authbrowser;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
            var j=new JdbcTemplate(ds);
            j.execute("CREATE TABLE ums_member(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_level_id BIGINT,username VARCHAR(64) UNIQUE,password VARCHAR(100),nickname VARCHAR(64),phone VARCHAR(64) UNIQUE,status INT,create_time TIMESTAMP,icon VARCHAR(500),gender INT,birthday DATE,city VARCHAR(64),job VARCHAR(100),personalized_signature VARCHAR(200),source_type INT,integration INT,growth INT,luckey_count INT,history_integration INT)");
            j.execute("CREATE TABLE sys_config(config_key VARCHAR(100) PRIMARY KEY,config_value VARCHAR(512))");
            j.update("INSERT INTO sys_config VALUES('membership.purchase.enabled','1')");
            j.execute("CREATE TABLE sys_message_record(id BIGINT AUTO_INCREMENT PRIMARY KEY,channel VARCHAR(30),target VARCHAR(100),content VARCHAR(500),biz_type VARCHAR(50),biz_id VARCHAR(100),status VARCHAR(50),error_msg VARCHAR(300),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            j.execute("CREATE TABLE ums_member_login_log(member_id BIGINT,create_time TIMESTAMP,login_type INT)");
            j.execute("CREATE TABLE sys_audit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,operator_name VARCHAR(100),operator_id BIGINT,module VARCHAR(50),action VARCHAR(100),target_id BIGINT,detail VARCHAR(2000))");
            j.execute("CREATE TABLE ums_member_recommend(member_id BIGINT PRIMARY KEY,inviter_id BIGINT)");
            j.execute("CREATE TABLE ums_points_account(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,points_type INT,balance BIGINT DEFAULT 0,frozen BIGINT DEFAULT 0,available BIGINT DEFAULT 0,version BIGINT DEFAULT 0,UNIQUE(member_id,points_type))");
            j.execute("CREATE TABLE ums_points_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,points_type INT,change_type VARCHAR(50),amount BIGINT,balance_after BIGINT,biz_id VARCHAR(100),idempotency_key VARCHAR(100) UNIQUE,operator_id BIGINT,remark VARCHAR(300),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            j.execute("CREATE TABLE ums_earnings(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT UNIQUE,total_earnings DECIMAL(18,2) DEFAULT 0,available_earnings DECIMAL(18,2) DEFAULT 0,frozen_earnings DECIMAL(18,2) DEFAULT 0,withdrawn DECIMAL(18,2) DEFAULT 0,version BIGINT DEFAULT 0)");
            j.execute("CREATE TABLE ums_earnings_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(50),amount DECIMAL(18,2),balance_after DECIMAL(18,2),biz_id VARCHAR(100),idempotency_key VARCHAR(100) UNIQUE,remark VARCHAR(300),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            j.execute("CREATE TABLE membership_purchase_order(id BIGINT AUTO_INCREMENT PRIMARY KEY,merchant_order_no VARCHAR(64) UNIQUE,request_id VARCHAR(64),member_id BIGINT,level VARCHAR(20),amount DECIMAL(18,2),product_points BIGINT,repurchase_points BIGINT,pay_channel VARCHAR(20),pay_mode VARCHAR(30),referrer_member_id BIGINT,referral_cash_rate DECIMAL(10,4) DEFAULT 0,referral_cash_cap DECIMAL(18,2) DEFAULT 0,referral_repurchase_points BIGINT DEFAULT 0,status VARCHAR(20),transaction_id VARCHAR(128),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,paid_at TIMESTAMP,UNIQUE(member_id,request_id))");
            String pools=Files.readString(Path.of("../document/sql/custom-v10/50_three_dividend_pools.sql"));
            pools=pools.substring(0,pools.indexOf("-- Menu/resource additions")).replaceAll("(?m)^--.*$","");
            for(String sql:pools.split(";")) if(!sql.isBlank()) j.execute(sql);
            return ds;
        }
        @Bean SqlSessionTemplate sql(DataSource ds) throws Exception {
            var configuration=new org.apache.ibatis.session.Configuration();configuration.setMapUnderscoreToCamelCase(true);
            for(Class<?> mapper:List.of(UmsMemberMapper.class,UmsPointsAccountMapper.class,UmsPointsLedgerMapper.class)) configuration.addMapper(mapper);
            var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(configuration);
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean UmsMemberMapper memberMapper(SqlSessionTemplate sql){return sql.getMapper(UmsMemberMapper.class);}
        @Bean UmsPointsAccountMapper pointsMapper(SqlSessionTemplate sql){return sql.getMapper(UmsPointsAccountMapper.class);}
        @Bean UmsPointsLedgerMapper pointsLedgerMapper(SqlSessionTemplate sql){return sql.getMapper(UmsPointsLedgerMapper.class);}
        @Bean UmsMemberLevelMapper memberLevels(){return mock(UmsMemberLevelMapper.class);}
        @Bean UmsMemberCacheService cache(){
            var cache=mock(UmsMemberCacheService.class);var codes=new ConcurrentHashMap<String,String>();
            doAnswer(c->{codes.put(c.getArgument(0),c.getArgument(1));return null;}).when(cache).setAuthCode(anyString(),anyString());
            when(cache.getAuthCode(anyString())).thenAnswer(c->codes.get(c.getArgument(0)));return cache;
        }
        @Bean SmsSendService sms(){return (phone,code)->SmsSendService.SmsSendResult.skipped("QA","isolated browser test");}
        @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder();}
        @Bean JwtTokenUtil tokens(){return new JwtTokenUtil();}
        @Bean JwtAuthenticationTokenFilter filter(){return new JwtAuthenticationTokenFilter();}
        @Bean RestAuthenticationEntryPoint entry(){return new RestAuthenticationEntryPoint();}
        @Bean RestfulAccessDeniedHandler denied(){return new RestfulAccessDeniedHandler();}
        @Bean IgnoreUrlsConfig urls(){var c=new IgnoreUrlsConfig();c.setUrls(List.of("/sso/login","/sso/register","/sso/getAuthCode","/error"));return c;}
        @Bean UserDetailsService users(UmsMemberService members){return members::loadUserByUsername;}
        @Bean MemberIdentityService identities(){return mock(MemberIdentityService.class);}
        @Bean MembershipReferralRewardService rewards(){return mock(MembershipReferralRewardService.class);}
        @Bean MembershipPaymentGateway gateway(){return new MembershipPaymentGateway(new MembershipPaymentProperties());}
    }
    public static void main(String[] args) {
        var app=new SpringApplication(App.class);app.setAdditionalProfiles("auth-browser-test");
        app.setDefaultProperties(Map.of("jwt.secret","isolated-auth-browser-secret-012345678901234567890123456789",
            "jwt.expiration","3600","jwt.tokenHeader","Authorization","jwt.tokenHead","Bearer ",
            "mall.payment.mock.enabled","true","mall.membership-v2.enabled","true",
            "spring.autoconfigure.exclude","com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure",
            "logging.level.root","WARN"));
        app.run(args);System.out.println("AUTH_BROWSER_READY: isolated auth, points and membership database");
    }
}
