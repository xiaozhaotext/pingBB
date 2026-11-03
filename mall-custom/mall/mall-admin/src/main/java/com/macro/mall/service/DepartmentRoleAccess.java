package com.macro.mall.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/** A department member has exactly the permissions of the department's role. */
@Component
public class DepartmentRoleAccess {
    private final JdbcTemplate jdbc;
    @Value("${mall.p0.admin-role-id:5}")
    private long platformRoleId;

    public DepartmentRoleAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** null: legacy administrator without a department; 0: department with no active role. */
    public Long roleId(Long adminId) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.dept_id,d.role_id,d.status,r.status AS role_status FROM ums_admin a "
                        + "LEFT JOIN ums_department d ON d.id=a.dept_id "
                        + "LEFT JOIN ums_role r ON r.id=d.role_id WHERE a.id=?", adminId);
        if (rows.isEmpty() || rows.get(0).get("dept_id") == null) return null;
        var row = rows.get(0);
        if (!(row.get("status") instanceof Number status) || status.intValue() != 1
                || !(row.get("role_status") instanceof Number roleStatus) || roleStatus.intValue() != 1
                || !(row.get("role_id") instanceof Number role)
                || role.longValue() == platformRoleId) return 0L;
        return role.longValue();
    }
}
