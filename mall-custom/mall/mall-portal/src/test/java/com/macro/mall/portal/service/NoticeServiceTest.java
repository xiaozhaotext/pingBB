package com.macro.mall.portal.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NoticeServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NoticeService service = new NoticeService(jdbc);

    @Test
    void homeInboxDetailAndUnreadCountOnlyIncludePublishedSystemAnnouncements() {
        service.announcements();
        service.memberNotices(5L);
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of("id", 1L)));
        service.detail(1L);
        service.unreadCount(5L);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        verify(jdbc, times(2)).queryForList(sql.capture(), any(Object[].class));
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), eq(5L));
        assertEquals(4, sql.getAllValues().size());
        for (String query : sql.getAllValues()) {
            assertTrue(query.contains("n.status=1 AND n.type='SYSTEM'"));
        }
        assertTrue(sql.getAllValues().get(0).contains("publish_time DESC,n.id DESC"));
    }

    @Test
    void unavailableNoticeDetailsReturnNotFound() {
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of());
        var error = assertThrows(ResponseStatusException.class, () -> service.detail(1L));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void hiddenOrDeletedNoticeCannotCreateAnOrphanReadMarker() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.markRead(1L, 5L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void readingAnAvailableNoticeUsesTheSameLockAsDeletionAndIsIdempotent() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(List.of(1L));
        service.markRead(1L, 5L);
        verify(jdbc).queryForList(contains("FOR UPDATE"), eq(Long.class), eq(1L));
        verify(jdbc).update("INSERT IGNORE INTO sys_notice_read(notice_id,member_id) VALUES(?,?)", 1L, 5L);
    }
}
