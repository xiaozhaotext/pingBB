package com.macro.mall.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
        Long platformAdmin = platformAdminId(principal);
        if (platformAdmin != null) return platformAdmin;
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            String path = servlet.getRequest().getRequestURI();
            List<java.util.Map<String, Object>> resources = jdbc.queryForList(
                    "SELECT a.id,r.url FROM ums_admin a JOIN ums_department d ON d.id=a.dept_id "
                            + "JOIN ums_role role ON role.id=d.role_id "
                            + "JOIN ums_role_resource_relation rr ON rr.role_id=role.id "
                            + "JOIN ums_resource r ON r.id=rr.resource_id "
                            + "WHERE a.username=? AND a.status=1 AND d.status=1 AND role.status=1",
                    principal.getName());
            AntPathMatcher matcher = new AntPathMatcher();
            for (var resource : resources) {
                if (resource.get("url") instanceof String url && matcher.match(url, path)) {
                    return ((Number) resource.get("id")).longValue();
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无此板块权限");
    }

    public long requirePlatformAdmin(Principal principal) {
        Long id = platformAdminId(principal);
        if (id != null) return id;
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台管理员可管理部门和子账号");
    }

    private Long platformAdminId(Principal principal) {
        if (principal == null) return null;
        List<Long> ids = jdbc.queryForList(
                "SELECT a.id FROM ums_admin a " +
                "JOIN ums_admin_role_relation r ON r.admin_id=a.id " +
                "WHERE a.username=? AND a.status=1 AND a.dept_id IS NULL AND r.role_id=? LIMIT 1",
                Long.class, principal.getName(), adminRoleId);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
