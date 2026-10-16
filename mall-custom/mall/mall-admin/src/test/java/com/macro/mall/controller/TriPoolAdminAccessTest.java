package com.macro.mall.controller;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TriPoolAdminAccessTest {
    @Test void membershipCardCannotUseOldRefundWithoutRewardReversal() {
        var jdbc=mock(JdbcTemplate.class);var payment=mock(com.macro.mall.service.PaymentRefundService.class);
        var service=new com.macro.mall.service.OrderReturnRefundService();
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        ReflectionTestUtils.setField(service,"paymentRefundService",payment);
        ReflectionTestUtils.setField(service,"membershipV2Enabled",true);
        when(jdbc.queryForList("SELECT order_id FROM tri_order_snapshot WHERE order_id=?",Long.class,10L)).thenReturn(List.of(10L));
        assertThrows(com.macro.mall.common.exception.ApiException.class,()->service.refundForReturn(1L,10L,new BigDecimal("3999"),"request"));
        verifyNoInteractions(payment);
    }
    @Test void oldMonthlyBenefitCannotBeNewlyRequestedUnderNewRules() {
        var jdbc=mock(JdbcTemplate.class);var access=mock(P0AdminAccess.class);
        var controller=new ManualBenefitController(jdbc,access);
        ReflectionTestUtils.setField(controller,"membershipV2Enabled",true);
        assertNotEquals(200,controller.request(()->"admin",Map.of()).getCode());
        verifyNoInteractions(jdbc);
    }
    @Test void membershipDoesNotExposeProductDesignation() {
        assertTrue(Arrays.stream(TriPoolAdminController.class.getDeclaredMethods()).noneMatch(m->m.getName().equals("membershipProduct")));
    }
    @Test void everyPoolReadAndMoneyWriteRequiresServerSideAdminAuthorization() {
        var jdbc=mock(JdbcTemplate.class);var pools=mock(TriPoolService.class);var access=mock(P0AdminAccess.class);
        var controller=new TriPoolAdminController(jdbc,pools,access);Principal p=()->"unprivileged";
        when(access.requireAuthorized(p)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class,()->controller.overview(p));
        assertThrows(ResponseStatusException.class,()->controller.records(p,"PARTNER",1));
        assertThrows(ResponseStatusException.class,()->controller.ledger(p,"PARTNER",1));
        assertThrows(ResponseStatusException.class,()->controller.members(p,1));
        assertThrows(ResponseStatusException.class,()->controller.preview(p));
        assertThrows(ResponseStatusException.class,()->controller.operationsMember(p,new TriPoolAdminController.OperationsMember(1,1,true,"test reason")));
        assertThrows(ResponseStatusException.class,()->controller.distribute(p,new TriPoolAdminController.Distribution("operations_request_0001",BigDecimal.TEN,1,"fake")));
        verifyNoInteractions(jdbc,pools);
    }
}
