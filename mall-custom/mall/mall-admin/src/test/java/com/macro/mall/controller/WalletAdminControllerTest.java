package com.macro.mall.controller;

import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletAdminControllerTest {
    JdbcTemplate jdbc;
    MockMvc mvc;
    P0AdminAccess access;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE ums_admin(id BIGINT,username VARCHAR(50),status INT)");
        jdbc.execute("CREATE TABLE ums_admin_role_relation(admin_id BIGINT,role_id BIGINT)");
        jdbc.update("INSERT INTO ums_admin VALUES(1,'admin',1),(2,'viewer',1)");
        jdbc.update("INSERT INTO ums_admin_role_relation VALUES(1,5),(2,9)");
        jdbc.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY,username VARCHAR(50))");
        jdbc.update("INSERT INTO ums_member VALUES(1,'wallet-user'),(2,'other-user')");
        jdbc.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT)");
        jdbc.update("INSERT INTO ums_recharge_account VALUES(1,11950),(2,123)");
        jdbc.execute("CREATE TABLE ums_recharge_order(order_no VARCHAR(50),member_id BIGINT,amount_cents BIGINT,channel VARCHAR(10),status VARCHAR(20),transaction_id VARCHAR(80),balance_after_cents BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,paid_at TIMESTAMP)");
        jdbc.update("INSERT INTO ums_recharge_order(order_no,member_id,amount_cents,channel,status,balance_after_cents) VALUES('RC1',1,10010,'MOCK','PAID',10010),('RC2',1,10000,'MOCK','PENDING',NULL),('RC3',2,123,'MOCK','PAID',123)");
        jdbc.execute("CREATE TABLE ums_recharge_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(50),amount_cents BIGINT,balance_after_cents BIGINT,biz_id VARCHAR(80),remark VARCHAR(200),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ums_recharge_ledger(member_id,change_type,amount_cents,balance_after_cents,biz_id) VALUES(1,'sandbox_recharge',10010,10010,'RC1'),(1,'sandbox_withdrawal',9940,19950,'WITHDRAW:1'),(1,'order_pay',-8000,11950,'101'),(2,'sandbox_recharge',123,123,'RC3')");
        access = new P0AdminAccess(jdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(access,"adminRoleId",5L);
        mvc = MockMvcBuilders.standaloneSetup(new WalletAdminController(jdbc,access)).build();
    }
    @Test void walletMatchesConsumerBalanceAndExcludesPendingRecharge() throws Exception {
        mvc.perform(get("/p0/wallet").principal(()->"admin").param("memberId","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.summary.balance").value("119.50"))
            .andExpect(jsonPath("$.data.summary.recharged").value("100.10"))
            .andExpect(jsonPath("$.data.summary.spent").value("80.00"))
            .andExpect(jsonPath("$.data.summary.withdrawalTransferred").value("99.40"))
            .andExpect(jsonPath("$.data.rows[0].balance").value("119.50"))
            .andExpect(jsonPath("$.data.rows[0].username").value("wallet-user"))
            .andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void ledgerFilterAndPaginationKeepSignedExactAmounts() throws Exception {
        mvc.perform(get("/p0/wallet").principal(()->"admin").param("tab","ledger").param("memberId","1").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.rows[0].amount").value("-80.00"))
            .andExpect(jsonPath("$.data.rows[0].balance_after").value("119.50"));
        mvc.perform(get("/p0/wallet").principal(()->"admin").param("tab","recharges").param("memberId","1"))
            .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/p0/wallet").principal(()->"admin"))
            .andExpect(jsonPath("$.data.summary.balance").value("120.73"));
    }
    @Test void walletRequiresPlatformAdminAndValidFilters() throws Exception {
        mvc.perform(get("/p0/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/p0/wallet").principal(()->"viewer")).andExpect(status().isForbidden());
        mvc.perform(get("/p0/wallet").principal(()->"admin").param("memberId","0"))
            .andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/p0/wallet").principal(()->"admin").param("tab","arbitrary-table"))
            .andExpect(jsonPath("$.code").value(404));
    }
    @Test void financeCountsCompletedSandboxWithdrawalsAtTheirNetAmount() {
        jdbc.execute("CREATE TABLE ums_earnings(member_id BIGINT,total_earnings DECIMAL(18,2),available_earnings DECIMAL(18,2),frozen_earnings DECIMAL(18,2),withdrawn DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE ums_earnings_ledger(id BIGINT,member_id BIGINT,change_type VARCHAR(50),amount DECIMAL(18,2),balance_after DECIMAL(18,2),biz_id VARCHAR(80),remark VARCHAR(100),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE ums_withdraw_apply(id BIGINT,member_id BIGINT,amount DECIMAL(18,2),fee_rate DECIMAL(10,6),fee_amount DECIMAL(18,2),actual_amount DECIMAL(18,2),status INT,simulate_flag INT,audit_id BIGINT,audit_remark VARCHAR(100),apply_time TIMESTAMP)");
        jdbc.update("INSERT INTO ums_withdraw_apply(id,member_id,amount,actual_amount,status) VALUES(1,1,100,99.40,3),(2,1,50,49.70,2),(3,1,20,19.88,0)");
        var result = new P0AdminController(jdbc,access,null).finance(()->"admin");
        var stats = (java.util.Map<?,?>)result.getData().get("withdrawStats");
        org.junit.jupiter.api.Assertions.assertEquals(2,((Number)stats.get("completed")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals(0,new java.math.BigDecimal("149.10").compareTo((java.math.BigDecimal)stats.get("paid_out")));
    }
}
