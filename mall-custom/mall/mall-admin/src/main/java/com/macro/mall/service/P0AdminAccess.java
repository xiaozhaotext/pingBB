package com.macro.mall.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

/** Explicit server-side guard in addition to mall's resource-based RBAC. */
@Component
public class P0AdminAccess {
    private final JdbcTemplate jdbc;

    @Value("${mall.p0.admin-role-id:5}")
    private long adminRoleId;

    public P0AdminAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long requireAuthorized(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT a.id FROM ums_admin a " +
                "JOIN ums_admin_role_relation r ON r.admin_id=a.id " +
                "WHERE a.username=? AND a.status=1 AND r.role_id=? LIMIT 1",
                Long.class, principal.getName(), adminRoleId);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅授权的平台管理员可操作");
        }
        return ids.get(0);
    }
}
