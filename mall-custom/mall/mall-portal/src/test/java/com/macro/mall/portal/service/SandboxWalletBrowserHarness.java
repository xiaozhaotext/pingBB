package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.membership.TriPoolService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.util.*;
import java.security.Principal;

/** Isolated local UI harness. Real wallet/order/earnings SQL; member login, catalog and admin menus are test adapters. */
public class SandboxWalletBrowserHarness {
    @Configuration @Profile("sandbox-wallet-test")
    @Import(Endpoints.class)
    static class BrowserConfig {
        @Bean Filter adminPrincipal() {
            return (request,response,chain)-> {
                var http=(HttpServletRequest)request;
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override public Principal getUserPrincipal() {
                        return "Bearer qa-wallet-admin".equals(http.getHeader("Authorization"))?()->"qa-admin":null;
                    }
                },response);
            };
        }
    }
    @RestController @Profile("sandbox-wallet-test")
    static class Endpoints {
        private final JdbcTemplate jdbc;
        Endpoints(JdbcTemplate jdbc) { this.jdbc=jdbc; }
        @GetMapping("/sso/info") CommonResult<?> member() {return CommonResult.success(Map.of("id",1,"username","sandbox-test","nickname","钱包验收用户"));}
        @PostMapping("/sso/login") CommonResult<?> login() {return CommonResult.success(Map.of("tokenHead","Bearer ","token","qa-wallet-member"));}
        @PostMapping("/admin/login") CommonResult<?> adminLogin() {return CommonResult.success(Map.of("tokenHead","Bearer ","token","qa-wallet-admin"));}
        @GetMapping("/admin/info") CommonResult<?> adminInfo() {
            var names=List.of("financeMgr","financeWalletManagement","financeSpendableWallet");
            var menus=new ArrayList<Map<String,Object>>();
            for(int i=0;i<names.size();i++) menus.add(Map.of("id",100+i,"parentId",i==0?0:99+i,"name",names.get(i),"title",List.of("财务中心","钱包明细管理","消费余额与充值").get(i),"hidden",0,"sort",100-i));
            return CommonResult.success(Map.of("username","qa-admin","roles",List.of("平台管理员"),"menus",menus,"icon",""));
        }
        @GetMapping("/qa/state") CommonResult<?> state() {return CommonResult.success(Map.of("wallet",jdbc.queryForList("SELECT * FROM ums_recharge_account"),"orders",jdbc.queryForList("SELECT * FROM qa_order"),"stock",jdbc.queryForList("SELECT * FROM qa_stock")));}
    }
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(SandboxWalletHttpTest.App.class,BrowserConfig.class,
            Class.forName("com.macro.mall.controller.WalletAdminController"),Class.forName("com.macro.mall.service.P0AdminAccess"));
        app.setAdditionalProfiles("sandbox-wallet-test");
        var context=app.run(args);
        var setup=new SandboxWalletHttpTest();
        setup.j=context.getBean(JdbcTemplate.class);setup.recharge=context.getBean(RechargeService.class);
        setup.members=context.getBean(UmsMemberService.class);setup.pools=context.getBean(TriPoolService.class);
        setup.setup();
        setup.j.execute("CREATE TABLE ums_admin(id BIGINT,username VARCHAR(50),status INT)");
        setup.j.execute("CREATE TABLE ums_admin_role_relation(admin_id BIGINT,role_id BIGINT)");
        setup.j.update("INSERT INTO ums_admin VALUES(1,'qa-admin',1)");setup.j.update("INSERT INTO ums_admin_role_relation VALUES(1,5)");
        System.out.println("WALLET_BROWSER_READY: isolated empty wallet, SKU price 80.00, stock 10");
    }
}
