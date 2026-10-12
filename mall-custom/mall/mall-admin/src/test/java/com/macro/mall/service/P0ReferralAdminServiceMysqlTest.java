package com.macro.mall.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only against an isolated local MySQL 8 schema with the P0 invitation and audit tables. */
class P0ReferralAdminServiceMysqlTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private P0ReferralAdminService service;

    @BeforeEach
    void setUp() {
        String url = System.getenv("MALL_MEMBERSHIP_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "isolated MySQL URL not supplied");
        assertTrue(url.contains("127.0.0.1") || url.contains("localhost"), "test DB must be local");
        var dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("MALL_MEMBERSHIP_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("MALL_MEMBERSHIP_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new P0ReferralAdminService(jdbc);
    }

    @Test
    void reassignsWholeBranchAndRejectsCycle() {
        long oldParent = member();
        long newParent = member();
        long member = member();
        long child = member();
        long grandchild = member();
        recommend(member, oldParent, "/" + oldParent + "/" + member);
        recommend(child, member, "/" + oldParent + "/" + member + "/" + child);
        recommend(grandchild, child, "/" + oldParent + "/" + member + "/" + child + "/" + grandchild);

        transaction.executeWithoutResult(status ->
                service.reassign(member, newParent, 8, "platform-admin", "人工核对推荐关系"));
        assertEquals(newParent, number("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?", member));
        assertEquals("/" + newParent + "/" + member,
                text("SELECT bind_path FROM ums_member_recommend WHERE member_id=?", member));
        assertEquals("/" + newParent + "/" + member + "/" + child,
                text("SELECT bind_path FROM ums_member_recommend WHERE member_id=?", child));
        assertEquals("/" + newParent + "/" + member + "/" + child + "/" + grandchild,
                text("SELECT bind_path FROM ums_member_recommend WHERE member_id=?", grandchild));
        assertEquals(1, number("SELECT COUNT(*) FROM sys_audit_log WHERE target_type='ums_member_recommend' AND target_id=?", member));
        assertEquals(1, number("SELECT COUNT(*) FROM ums_member_recommend_audit WHERE member_id=? AND action='admin_reassign'", member));

        assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status ->
                service.reassign(newParent, grandchild, 8, "platform-admin", "不应形成推荐循环")));
        assertEquals("/" + newParent + "/" + member,
                text("SELECT bind_path FROM ums_member_recommend WHERE member_id=?", member));
    }

    private long member() {
        long id = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 1);
        jdbc.update("INSERT INTO ums_member(id) VALUES(?)", id);
        return id;
    }

    private void recommend(long member, long inviter, String path) {
        jdbc.update("INSERT INTO ums_member_recommend(member_id,inviter_id,bind_path) VALUES(?,?,?)",
                member, inviter, path);
    }

    private long number(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
