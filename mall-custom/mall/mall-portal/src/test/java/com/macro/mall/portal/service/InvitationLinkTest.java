package com.macro.mall.portal.service;

import com.macro.mall.portal.service.impl.InvitationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvitationLinkTest {
    @Test
    void qrLinkOpensRegistrationWithPrefilledCode() {
        InvitationServiceImpl service = new InvitationServiceImpl();
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://122.51.105.107/sandbox");

        String link = ReflectionTestUtils.invokeMethod(service, "invitationLink", "779JPAEL");

        assertEquals("https://122.51.105.107/sandbox/#/pages/public/register?inviteCode=779JPAEL", link);
    }
}
