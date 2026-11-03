package com.macro.mall.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartmentRoleAccessTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DepartmentRoleAccess access = new DepartmentRoleAccess(jdbc);

    @Test void departmentRoleIsExclusiveAndDisabledDepartmentHasNoRole() {
        ReflectionTestUtils.setField(access, "platformRoleId", 5L);
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of(
                "dept_id", 3L, "role_id", 12L, "status", 1, "role_status", 1)));
        assertEquals(12L, access.roleId(7L));

        when(jdbc.queryForList(anyString(), eq(8L))).thenReturn(List.of(Map.of(
                "dept_id", 3L, "role_id", 12L, "status", 0, "role_status", 1)));
        assertEquals(0L, access.roleId(8L));

        when(jdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(Map.of(
                "dept_id", 3L, "role_id", 5L, "status", 1, "role_status", 1)));
        assertEquals(0L, access.roleId(10L));

        when(jdbc.queryForList(anyString(), eq(9L))).thenReturn(List.of(Map.of("id", 9L)));
        assertNull(access.roleId(9L));
    }
}
