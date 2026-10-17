package com.macro.mall.controller;

import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.P0ReferralAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MemberLoginCountTest {
    JdbcTemplate jdbc;
    P0AdminController controller;

    @BeforeEach void setup() {
        // Keep the database alive across JdbcTemplate's separate connections.
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE ums_admin(id BIGINT,username VARCHAR(50),status INT)");
        jdbc.execute("CREATE TABLE ums_admin_role_relation(admin_id BIGINT,role_id BIGINT)");
        jdbc.update("INSERT INTO ums_admin VALUES(3,'admin',1)");
        jdbc.update("INSERT INTO ums_admin_role_relation VALUES(3,5)");
        jdbc.execute("CREATE TABLE ums_member(id BIGINT,username VARCHAR(50),nickname VARCHAR(50),phone VARCHAR(20),gender INT,status INT,create_time TIMESTAMP)");
        jdbc.update("INSERT INTO ums_member VALUES(1,'active-user','活跃会员','19900000001',0,1,NOW()),(2,'new-user','新会员','19900000002',0,1,NOW())");
        jdbc.execute("CREATE TABLE ums_member_contact(member_id BIGINT,real_name VARCHAR(50),email VARCHAR(50),wechat_account VARCHAR(50))");
        jdbc.execute("CREATE TABLE ums_member_identity(member_id BIGINT,identity_type INT)");
        jdbc.execute("CREATE TABLE ums_member_recommend(member_id BIGINT,inviter_id BIGINT)");
        jdbc.execute("CREATE TABLE ums_recharge_account(member_id BIGINT,balance_cents BIGINT)");
        jdbc.execute("CREATE TABLE ums_earnings(member_id BIGINT,available_earnings DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE ums_points_account(member_id BIGINT,points_type INT,available BIGINT)");
        jdbc.execute("CREATE TABLE ums_member_login_log(member_id BIGINT,create_time TIMESTAMP)");
        jdbc.update("INSERT INTO ums_member_login_log VALUES(1,NOW()),(1,NOW()),(1,'2026-01-01 00:00:00')");
        jdbc.execute("CREATE TABLE ums_member_statistics_info(member_id BIGINT,login_count BIGINT)");
        jdbc.update("INSERT INTO ums_member_statistics_info VALUES(1,99)");
        P0AdminAccess access = new P0AdminAccess(jdbc);
        ReflectionTestUtils.setField(access, "adminRoleId", 5L);
        controller = new P0AdminController(jdbc, access, mock(P0ReferralAdminService.class));
    }

    @Test void usesActualLoginLogsInsteadOfMissingOrStaleStatistics() {
        var rows = controller.members(() -> "admin", "", 1, 20).getData();
        assertEquals(2, rows.size());
        var active = rows.stream().filter(row -> ((Number)row.get("id")).longValue() == 1).findFirst().orElseThrow();
        var fresh = rows.stream().filter(row -> ((Number)row.get("id")).longValue() == 2).findFirst().orElseThrow();
        assertEquals(3L, ((Number)active.get("login_count")).longValue());
        assertEquals(0L, ((Number)fresh.get("login_count")).longValue());
    }

    @Test void paginationAndSearchKeepCountsScopedToEachMember() {
        var page = controller.members(() -> "admin", "", 2, 1).getData();
        assertEquals(1, page.size());
        assertEquals(1L, ((Number)page.get(0).get("id")).longValue());
        assertEquals(3L, ((Number)page.get(0).get("login_count")).longValue());
        var result = controller.members(() -> "admin", "new-user", 1, 20).getData();
        assertEquals(1, result.size());
        assertEquals(0L, ((Number)result.get(0).get("login_count")).longValue());
    }
}
