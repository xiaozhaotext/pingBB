package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipPaymentOptionsTest {
    @Test void sandboxAdvertisesOnlyMockAndDoesNotReadProviderCredentials() {
        var jdbc = mock(JdbcTemplate.class);
        var gateway = mock(MembershipPaymentGateway.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("membership.purchase.enabled"))).thenReturn(List.of("1"));
        var service = new MembershipPurchaseOrderService(jdbc, null, gateway, null, null, true);
        var options = service.paymentOptions();
        assertEquals(true, options.get("sandbox"));
        assertEquals(true, options.get("purchaseEnabled"));
        assertEquals(false, options.get("wechatReady"));
        assertEquals(false, options.get("alipayReady"));
        verifyNoInteractions(gateway);
    }

    @Test void productionWithoutCredentialsDoesNotOfferMockOrRealPayment() {
        var gateway = mock(MembershipPaymentGateway.class);
        doThrow(new MembershipPaymentException(MembershipPaymentError.CHANNEL_UNAVAILABLE)).when(gateway).requireReady(anyString());
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("membership.purchase.enabled"))).thenReturn(List.of());
        var service = new MembershipPurchaseOrderService(jdbc, null, gateway, null, null, false);
        var options = service.paymentOptions();
        assertEquals(false, options.get("sandbox"));
        assertEquals(false, options.get("purchaseEnabled"));
        assertEquals(false, options.get("wechatReady"));
        assertEquals(false, options.get("alipayReady"));
        verify(gateway, never()).start(any(), any(), any(), any(), any(), any(), any());
    }
}
