package com.macro.mall.controller;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.SiteProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SiteProfileAdminControllerTest {
    @Test void unauthorizedWritesNeverCallService() {
        var access=mock(P0AdminAccess.class);var service=mock(SiteProfileService.class);Principal p=()->"merchant-employee";
        when(access.requireAuthorized(p)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));var c=new SiteProfileAdminController(access,service);
        assertThrows(ResponseStatusException.class,()->c.company(p,Map.of()));assertThrows(ResponseStatusException.class,()->c.draft(p,Map.of()));assertThrows(ResponseStatusException.class,()->c.publish(p,Map.of()));verifyNoInteractions(service);
    }
    @Test void authorizedOperationUsesAuthenticatedOperator() {
        var access=mock(P0AdminAccess.class);var service=mock(SiteProfileService.class);Principal p=()->"admin";when(access.requireAuthorized(p)).thenReturn(3L);var c=new SiteProfileAdminController(access,service);var body=Map.<String,Object>of("revision",0,"operatorId",999);
        c.company(p,body);verify(service).company(body,3L,"admin");
    }
}
