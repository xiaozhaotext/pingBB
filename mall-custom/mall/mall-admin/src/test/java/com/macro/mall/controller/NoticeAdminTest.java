package com.macro.mall.controller;

import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NoticeAdminTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final P0AdminAccess access = mock(P0AdminAccess.class);
    private final P0AdminController controller = new P0AdminController(jdbc, access, null);
    private final Principal principal = () -> "operator";

    @BeforeEach
    void setup() {
        when(access.requireAuthorized(principal)).thenReturn(5L);
        when(jdbc.queryForList("SELECT id,title,status FROM sys_notice WHERE id=? FOR UPDATE", 1L))
                .thenReturn(List.of(Map.of("id", 1L, "title", "原公告", "status", 1)));
    }

    @Test
    void everyMutationRequiresAuthorizationBeforeReadingOrWritingNoticeData() {
        when(access.requireAuthorized(principal)).thenThrow(new SecurityException());
        assertThrows(SecurityException.class, () -> controller.updateNotice(principal, 1L, Map.of()));
        assertThrows(SecurityException.class, () -> controller.updateNoticeStatus(principal, 1L, Map.of("status", 0)));
        assertThrows(SecurityException.class, () -> controller.deleteNotice(principal, 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void editingUpdatesContentWithoutChangingItsPublicationStateAndRecordsAudit() {
        controller.updateNotice(principal, 1L, Map.of("title", "新公告", "content", "新正文"));
        verify(jdbc).update("UPDATE sys_notice SET title=?,content=? WHERE id=?", "新公告", "新正文", 1L);
        verify(jdbc).update(startsWith("INSERT INTO sys_audit_log"), eq(5L), eq("operator"), eq("notice"), eq("update"), eq(1L), anyString());
    }

    @Test
    void blankContentAndInvalidStatusAreRejectedBeforeDataMutation() {
        assertThrows(ResponseStatusException.class, () -> controller.updateNotice(principal, 1L, Map.of("title", " ", "content", "正文")));
        assertThrows(ResponseStatusException.class, () -> controller.updateNoticeStatus(principal, 1L, Map.of("status", 2)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void unpublishKeepsContentAndOriginalPublicationTime() {
        controller.updateNoticeStatus(principal, 1L, Map.of("status", 0));
        verify(jdbc).update(startsWith("UPDATE sys_notice SET status=?"), eq(0), eq(0), eq(1L));
    }

    @Test
    void repeatingAnAlreadyAppliedStatusDoesNotPublishOrAuditAgain() {
        controller.updateNoticeStatus(principal, 1L, Map.of("status", 1));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void deleteRemovesReadMarkersBeforeNoticeAndKeepsAnAuditRecord() {
        controller.deleteNotice(principal, 1L);
        var ordered = inOrder(jdbc);
        ordered.verify(jdbc).queryForList("SELECT id,title,status FROM sys_notice WHERE id=? FOR UPDATE", 1L);
        ordered.verify(jdbc).update("DELETE FROM sys_notice_read WHERE notice_id=?", 1L);
        ordered.verify(jdbc).update("DELETE FROM sys_notice WHERE id=?", 1L);
        ordered.verify(jdbc).update(startsWith("INSERT INTO sys_audit_log"), eq(5L), eq("operator"), eq("notice"), eq("delete"), eq(1L), anyString());
    }

    @Test
    void editingAMissingNoticeDoesNotCreateAnotherOne() {
        when(jdbc.queryForList("SELECT id,title,status FROM sys_notice WHERE id=? FOR UPDATE", 1L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> controller.updateNotice(principal, 1L, Map.of("title", "标题", "content", "正文")));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
