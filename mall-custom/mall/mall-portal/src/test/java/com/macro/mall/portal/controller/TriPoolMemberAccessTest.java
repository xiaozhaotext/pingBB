package com.macro.mall.portal.controller;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TriPoolMemberAccessTest {
    @Test void visitorCannotSubmitMemberTravelBenefitThroughDirectApi() {
        var pools=mock(TriPoolService.class);var jdbc=mock(JdbcTemplate.class);var members=mock(UmsMemberService.class);
        var member=new UmsMember();member.setId(7L);when(members.getCurrentMember()).thenReturn(member);
        var controller=new TriPoolMemberController(pools,members,jdbc);
        assertThrows(ResponseStatusException.class,()->controller.travel(new TriPoolMemberController.TravelRequest("test route",3,1,"","123456")));
        verifyNoInteractions(jdbc);
    }
    @Test void dividendHistoryAlwaysUsesAuthenticatedMemberId() {
        var pools=mock(TriPoolService.class);var jdbc=mock(JdbcTemplate.class);var members=mock(UmsMemberService.class);
        var member=new UmsMember();member.setId(7L);when(members.getCurrentMember()).thenReturn(member);when(pools.enabled()).thenReturn(true);
        var controller=new TriPoolMemberController(pools,members,jdbc);
        controller.records(2);
        verify(jdbc).queryForList("SELECT id,batch_id,pool_code,identity_snapshot,shares,total_shares,cap_amount,received_before,amount,created_at FROM tri_pool_distribution WHERE member_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",7L,20);
    }
}
