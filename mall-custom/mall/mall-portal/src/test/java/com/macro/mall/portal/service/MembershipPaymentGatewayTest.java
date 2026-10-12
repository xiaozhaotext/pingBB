package com.macro.mall.portal.service;

import com.macro.mall.portal.config.MembershipPaymentProperties;
import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MembershipPaymentGatewayTest {
    @Test
    void allChannelsFailClosedWithoutMerchantCredentials() {
        MembershipPaymentGateway gateway = new MembershipPaymentGateway(new MembershipPaymentProperties());
        for (String channel : new String[]{"WECHAT", "ALIPAY"}) {
            MembershipPaymentException ex = assertThrows(MembershipPaymentException.class,
                    () -> gateway.requireReady(channel));
            assertEquals(MembershipPaymentError.CHANNEL_UNAVAILABLE, ex.getError());
        }
        assertEquals(MembershipPaymentError.CHANNEL_UNAVAILABLE,
                assertThrows(MembershipPaymentException.class,
                        () -> gateway.start("MP001", "PARTNER", new BigDecimal("3999.00"),
                                "WECHAT", "WECHAT_H5", null, "127.0.0.1")).getError());
        assertEquals(MembershipPaymentError.CHANNEL_UNAVAILABLE,
                assertThrows(MembershipPaymentException.class,
                        () -> gateway.verifyAlipay(Map.of("trade_status", "TRADE_SUCCESS"))).getError());
    }

}
