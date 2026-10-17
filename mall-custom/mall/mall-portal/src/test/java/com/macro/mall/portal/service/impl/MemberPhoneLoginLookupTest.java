package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.portal.service.UmsMemberCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberPhoneLoginLookupTest {
    @Test
    void fallsBackToPhoneWhenUsernameDoesNotExist() {
        UmsMemberServiceImpl service = new UmsMemberServiceImpl();
        UmsMemberMapper mapper = mock(UmsMemberMapper.class);
        UmsMemberCacheService cache = mock(UmsMemberCacheService.class);
        UmsMember member = new UmsMember();
        member.setId(79L);
        member.setUsername("tester");
        member.setPhone("13800000001");
        when(mapper.selectByExample(any(UmsMemberExample.class)))
                .thenReturn(Collections.emptyList(), List.of(member));
        ReflectionTestUtils.setField(service, "memberMapper", mapper);
        ReflectionTestUtils.setField(service, "memberCacheService", cache);

        assertEquals(79L, service.getByUsername("13800000001").getId());
        verify(mapper, times(2)).selectByExample(any(UmsMemberExample.class));
    }

    @Test
    void existingUsernameWinsBeforePhoneFallback() {
        UmsMemberServiceImpl service = new UmsMemberServiceImpl();
        UmsMemberMapper mapper = mock(UmsMemberMapper.class);
        UmsMemberCacheService cache = mock(UmsMemberCacheService.class);
        UmsMember member = new UmsMember();
        member.setId(1L);
        when(mapper.selectByExample(any(UmsMemberExample.class))).thenReturn(List.of(member));
        ReflectionTestUtils.setField(service, "memberMapper", mapper);
        ReflectionTestUtils.setField(service, "memberCacheService", cache);

        assertEquals(1L, service.getByUsername("13800000001").getId());
        verify(mapper, times(1)).selectByExample(any(UmsMemberExample.class));
    }
}
