package com.macro.mall.portal.controller;

import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberPoolPrivacyTest {
    @Test void personalSummaryDoesNotQueryOrExposePoolTotals() {
        var pools=mock(TriPoolService.class);var members=mock(UmsMemberService.class);var jdbc=mock(JdbcTemplate.class);
        var member=new UmsMember();member.setId(1001L);when(members.getCurrentMember()).thenReturn(member);
        var summary=Map.<String,Object>of("enabled",true,"totalCap",2400,"totalReceived",14.28,"totalRemaining",2385.72,"pools",List.of(Map.of("code","PARTNER","cap",2400,"received",14.28)));
        when(pools.memberSummary(1001)).thenReturn(summary);
        var result=new TriPoolMemberController(pools,members,jdbc).summary().getData();
        assertEquals(summary,result);assertFalse(result.containsKey("poolBalances"));verifyNoInteractions(jdbc);
    }
    @Test void legacyPoolTotalEndpointIsNotAvailableToMembers() {
        var controller=new DividendPoolController();var jdbc=mock(JdbcTemplate.class);ReflectionTestUtils.setField(controller,"jdbc",jdbc);
        assertEquals(403,controller.pool().getCode());verifyNoInteractions(jdbc);
    }
}
