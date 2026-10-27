package com.macro.mall.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.config.MembershipPaymentProperties;
import com.macro.mall.portal.config.OrderWechatPayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatWebOauthServiceTest {
    @Test void callbackRequiresSameBrowserCookieBeforeConsumingState() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var order = new OrderWechatPayProperties();
        order.setEnabled(true);
        order.setAppId("wx-test-app");
        var oauth = new WechatWebOauthService(redis, new ObjectMapper(), order,
                new MembershipPaymentProperties());
        ReflectionTestUtils.setField(oauth, "appSecret", "secret-not-logged");
        ReflectionTestUtils.setField(oauth, "callbackUrl", "https://example.com/portal/member/wechat-oauth/callback");
        ReflectionTestUtils.setField(oauth, "siteUrl", "https://example.com");

        var start = oauth.authorize(42, "ORDER", 99L);
        assertTrue(start.url().contains("scope=snsapi_base"));
        assertEquals(32, start.state().length());
        assertThrows(ResponseStatusException.class,
                () -> oauth.callback("provider-code", start.state(), "other-browser-state"));
        verify(values, never()).getAndDelete(anyString());
    }
}
