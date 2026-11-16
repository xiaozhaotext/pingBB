package com.macro.mall.controller;

import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.service.P0AdminAccess;
import java.math.BigDecimal;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只使用内存数据库，验证周预算保存、读取和权限限制。 */
class WeeklyBudgetControllerTest {
    JdbcTemplate jdbc;
    TriPoolAdminController controller;
    P0AdminAccess access;
    Principal admin = () -> "admin";

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:weekly_budget_"
                + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_config(config_key VARCHAR(100) PRIMARY KEY,config_value VARCHAR(100),config_group VARCHAR(50),remark VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_audit_log(operator_name VARCHAR(100),operator_id BIGINT,module VARCHAR(50),action VARCHAR(100),detail VARCHAR(2000))");
        jdbc.update("INSERT INTO sys_config VALUES('tri.pool.daily.budget.partner','300','distribution','')");
        access = mock(P0AdminAccess.class);
        when(access.requirePlatformAdmin(any())).thenReturn(1L);
        controller = new TriPoolAdminController(jdbc, mock(TriPoolService.class), access);
    }

    @Test void customWeeklyAmountsAreIndependentAndDailyBudgetIsUntouched() {
        controller.saveWeeklyBudget(admin, new TriPoolAdminController.WeeklyBudget("PARTNER", new BigDecimal("825.50")));
        controller.saveWeeklyBudget(admin, new TriPoolAdminController.WeeklyBudget("COFOUNDER", new BigDecimal("123.45")));
        var data = controller.weeklyBudget(admin).getData();
        assertEquals("825.50", data.get("tri.pool.weekly.budget.partner"));
        assertEquals("123.45", data.get("tri.pool.weekly.budget.cofounder"));
        assertEquals(2, data.size());
        assertEquals("300", jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key='tri.pool.daily.budget.partner'", String.class));
        verify(access).requireAuthorized(admin);
    }

    @Test void zeroBudgetIsAllowedAndCanBeChangedAgain() {
        controller.saveWeeklyBudget(admin, new TriPoolAdminController.WeeklyBudget("PARTNER", BigDecimal.ZERO));
        controller.saveWeeklyBudget(admin, new TriPoolAdminController.WeeklyBudget("PARTNER", new BigDecimal("500.00")));
        assertEquals("500.00", controller.weeklyBudget(admin).getData().get("tri.pool.weekly.budget.partner"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE action='WEEKLY_BUDGET'", Integer.class));
    }

    @Test void invalidAmountsCannotWriteConfigOrAudit() {
        for (String amount : new String[]{"-1", "1.001", "100000000.01"}) {
            assertThrows(IllegalArgumentException.class, () -> controller.saveWeeklyBudget(admin,
                    new TriPoolAdminController.WeeklyBudget("PARTNER", new BigDecimal(amount))));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_config WHERE config_key LIKE 'tri.pool.weekly.%'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log", Integer.class));
    }

    @Test void nonAdminCannotSetWeeklyBudget() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.saveWeeklyBudget(() -> "operator", new TriPoolAdminController.WeeklyBudget("PARTNER", BigDecimal.TEN)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log", Integer.class));
    }
}
