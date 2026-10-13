package com.macro.mall.portal.controller;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.DividendDistributionService;
import com.macro.mall.portal.service.MemberEarningsCreditService;
import com.macro.mall.portal.service.impl.UmsMemberServiceImpl;
import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GapSecurityTest {
    @Test void invalidRegistrationNeverReachesDatabaseOrOtpStorage() {
        UmsMemberServiceImpl service = new UmsMemberServiceImpl();
        assertThrows(ApiException.class, () -> service.register("", "StrongPass123", "13900000000", "123456"));
        assertThrows(ApiException.class, () -> service.register("member", "short", "13900000000", "123456"));
        assertThrows(ApiException.class, () -> service.register("member", "StrongPass123", "bad-phone", "123456"));
        assertThrows(ApiException.class, () -> service.register("member", "中".repeat(25), "13900000000", "123456"));
    }
    @Test void profileNeverSerializesPasswordAndDoesNotMutateCachedPrincipal() {
        UmsMember m = new UmsMember(); m.setId(7L); m.setUsername("member"); m.setPassword("HASH-MUST-NOT-LEAK");
        UmsMemberService service = mock(UmsMemberService.class); when(service.getCurrentMember()).thenReturn(m);
        UmsMemberController c = new UmsMemberController(); ReflectionTestUtils.setField(c,"memberService",service);
        Map<?,?> data = (Map<?,?>)c.info(() -> "member").getData();
        assertFalse(data.containsKey("password")); assertEquals(7L,data.get("id"));
        assertEquals("HASH-MUST-NOT-LEAK",m.getPassword());
    }
    @Test void missingConsultationFieldsNeverWriteDatabase() {
        ConsultationController c = consultation();
        assertNotEquals(200,c.submit(Map.of()).getCode());
        verifyNoInteractions((JdbcTemplate)ReflectionTestUtils.getField(c,"jdbc"));
    }
    @ParameterizedTest @ValueSource(strings={"", "   "})
    void emptyConsultationRejected(String value) {
        ConsultationController c = consultation();
        assertNotEquals(200,c.submit(Map.of("subject",value,"content","text")).getCode());
        verifyNoInteractions((JdbcTemplate)ReflectionTestUtils.getField(c,"jdbc"));
    }
    @Test void structuredSubjectAndOversizeContactRejected() {
        ConsultationController c = consultation();
        assertNotEquals(200,c.submit(Map.of("subject",Map.of(),"content","text")).getCode());
        assertNotEquals(200,c.submit(Map.of("subject","title","content","text","contact","x".repeat(129))).getCode());
        verifyNoInteractions((JdbcTemplate)ReflectionTestUtils.getField(c,"jdbc"));
    }
    @Test void consumerCannotTriggerAnyDividendWrite() {
        DividendPoolController c = new DividendPoolController();
        assertEquals(403,c.distribute(Map.of()).getCode());
        assertEquals(403,c.allocate(Map.of()).getCode());
        assertEquals(403,c.contribution(Map.of()).getCode());
    }
    @Test void dividendPostingHonoursOperatorSwitch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // 后台把 dividend.distribution.enabled 关掉后，分红发放必须直接拒绝
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(java.util.List.of("0"));
        DividendDistributionService service =
                new DividendDistributionService(jdbc, null, new MemberEarningsCreditService(jdbc));
        assertThrows(IllegalStateException.class, () -> service.distributeDaily(LocalDate.now()));
    }
    @Test void dividendPostingStaysOffEvenIfExistingDatabaseSwitchIsOn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(java.util.List.of("1"));
        DividendDistributionService service =
                new DividendDistributionService(jdbc, null, new MemberEarningsCreditService(jdbc));
        assertThrows(IllegalStateException.class, () -> service.distributeDaily(LocalDate.now()));
        verifyNoInteractions(jdbc);
    }
    private ConsultationController consultation() {
        UmsMember m = new UmsMember(); m.setId(7L);
        UmsMemberService service = mock(UmsMemberService.class); when(service.getCurrentMember()).thenReturn(m);
        ConsultationController c = new ConsultationController();
        ReflectionTestUtils.setField(c,"memberService",service); ReflectionTestUtils.setField(c,"jdbc",mock(JdbcTemplate.class));
        return c;
    }
}
