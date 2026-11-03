package com.macro.mall.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P0DepartmentResourceTest {
    @Test void broadPlatformGrantCannotAuthorizeDepartmentMember() {
        assertFalse(P0AdminAccess.departmentResourceMatches("/p0/**", "/p0/finance"));
        assertTrue(P0AdminAccess.departmentResourceMatches("/p0/finance/**", "/p0/finance"));
        assertFalse(P0AdminAccess.departmentResourceMatches("/p0/finance/**", "/p0/members"));
    }
}
