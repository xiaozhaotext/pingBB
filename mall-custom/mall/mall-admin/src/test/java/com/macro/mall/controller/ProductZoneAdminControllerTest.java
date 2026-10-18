package com.macro.mall.controller;

import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductZoneAdminControllerTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final P0AdminAccess access=mock(P0AdminAccess.class);
    final Principal principal=()->"admin";
    final ProductZoneAdminController controller=new ProductZoneAdminController(jdbc,access);

    @Test void unauthorizedWriteHasNoDatabaseSideEffects() {
        when(access.requireAuthorized(principal)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class,()->controller.assign(principal,"REBUY",new ProductZoneAdminController.Assignment(7L,0)));
        verifyNoInteractions(jdbc);
    }
    @Test void consumeZoneAcceptsProducts() {
        when(access.requireAuthorized(principal)).thenReturn(3L);
        when(jdbc.queryForList(anyString(),eq(Long.class),eq(7L))).thenReturn(List.of(7L));
        when(jdbc.queryForList(anyString(),eq(Integer.class),eq(7L),eq("CONSUME"))).thenReturn(List.of());
        controller.assign(principal,"CONSUME",new ProductZoneAdminController.Assignment(7L,0));
        verify(jdbc).update(anyString(),eq(7L),eq("CONSUME"),eq(0));
    }
    @Test void unknownZoneCannotAcceptProducts() {
        when(access.requireAuthorized(principal)).thenReturn(3L);
        ResponseStatusException e=assertThrows(ResponseStatusException.class,()->controller.assign(principal,"UNKNOWN",new ProductZoneAdminController.Assignment(7L,0)));
        assertEquals(HttpStatus.BAD_REQUEST,e.getStatusCode());
        verifyNoInteractions(jdbc);
    }
    @Test void retryDoesNotRepeatAssignmentOrAudit() {
        when(access.requireAuthorized(principal)).thenReturn(3L);
        when(jdbc.queryForList(anyString(),eq(Long.class),eq(7L))).thenReturn(List.of(7L));
        when(jdbc.queryForList(anyString(),eq(Integer.class),eq(7L),eq("REBUY"))).thenReturn(List.of(5));
        controller.assign(principal,"REBUY",new ProductZoneAdminController.Assignment(7L,5));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void missingProductCannotBeAssigned() {
        when(access.requireAuthorized(principal)).thenReturn(3L);
        when(jdbc.queryForList(anyString(),eq(Long.class),eq(7L))).thenReturn(List.of());
        ResponseStatusException e=assertThrows(ResponseStatusException.class,()->controller.assign(principal,"PICK",new ProductZoneAdminController.Assignment(7L,5)));
        assertEquals(HttpStatus.NOT_FOUND,e.getStatusCode());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void pickAssignmentRequiresExplicitTourRouteAnd3999Prices() {
        when(access.requireAuthorized(principal)).thenReturn(3L);
        when(jdbc.queryForList(anyString(),eq(Long.class),eq(7L))).thenReturn(List.of(7L));
        assertThrows(ResponseStatusException.class,()->controller.assign(principal,"PICK",
                new ProductZoneAdminController.Assignment(7L,5)));
        when(jdbc.queryForObject(contains("SELECT price FROM pms_product"),eq(BigDecimal.class),eq(7L)))
                .thenReturn(new BigDecimal("3999.00"));
        when(jdbc.queryForObject(contains("price IS NULL"),eq(Integer.class),eq(7L))).thenReturn(0);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM pms_sku_stock WHERE product_id=?"),eq(Integer.class),eq(7L))).thenReturn(1);
        when(jdbc.queryForList(anyString(),eq(Integer.class),eq(7L),eq("PICK"))).thenReturn(List.of());
        controller.assign(principal,"PICK",new ProductZoneAdminController.Assignment(7L,5,true));
        verify(jdbc).update(contains("tri_membership_product"),eq(7L),eq(3L));
    }
}
