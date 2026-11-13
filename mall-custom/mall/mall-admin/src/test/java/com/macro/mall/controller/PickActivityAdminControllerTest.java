package com.macro.mall.controller;

import com.macro.mall.common.service.PickActivityService;
import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickActivityAdminControllerTest {
    @Test void unauthorizedSaveCannotWriteActivity() {
        var service = mock(PickActivityService.class);
        var access = mock(P0AdminAccess.class);
        when(access.requireAuthorized(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var controller = new PickActivityAdminController(service, access);
        assertThrows(ResponseStatusException.class, () -> controller.save(null, null));
        verifyNoInteractions(service);
    }

    @Test void authorizedCategoryManagerCanSaveAndReadActivities() {
        var service = mock(PickActivityService.class);
        var access = mock(P0AdminAccess.class);
        Principal principal = () -> "分类管理员";
        when(access.requireAuthorized(principal)).thenReturn(1L);
        var input = new PickActivityService.Activity(null,"活动","/product-images/1.png","规则",true,1,List.of(1L));
        when(service.save(input)).thenReturn(7L);
        var controller = new PickActivityAdminController(service, access);
        assertEquals(7L, controller.save(principal, input).getData());
        controller.list(principal);
        controller.detail(principal, 7);
        controller.products(principal, "旅游");
        verify(service).list(false);
        verify(service).detail(7, false);
        verify(service).candidates("旅游");
        verify(access, times(4)).requireAuthorized(principal);
    }
}
