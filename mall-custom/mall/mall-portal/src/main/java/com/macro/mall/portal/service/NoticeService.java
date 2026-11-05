package com.macro.mall.portal.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** The homepage, inbox, detail and unread count share the same published announcement scope. */
@Service
public class NoticeService {
    private static final String PUBLISHED = "n.status=1 AND n.type='SYSTEM'";
    private static final String FIELDS = "n.id,n.title,n.content,n.type,n.publish_time";
    private static final String ORDER = " ORDER BY n.publish_time DESC,n.id DESC";
    private final JdbcTemplate jdbc;

    public NoticeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> announcements() {
        return jdbc.queryForList("SELECT " + FIELDS + " FROM sys_notice n WHERE " + PUBLISHED + ORDER + " LIMIT 5");
    }

    public Map<String, Object> detail(long noticeId) {
        var rows = jdbc.queryForList("SELECT " + FIELDS + " FROM sys_notice n WHERE " + PUBLISHED + " AND n.id=?", noticeId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告已下线或已删除");
        return rows.get(0);
    }

    public List<Map<String, Object>> memberNotices(long memberId) {
        return jdbc.queryForList("SELECT " + FIELDS + ",CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS is_read"
                + " FROM sys_notice n LEFT JOIN sys_notice_read r ON r.notice_id=n.id AND r.member_id=?"
                + " WHERE " + PUBLISHED + ORDER + " LIMIT 50", memberId);
    }

    @Transactional
    public void markRead(long noticeId, long memberId) {
        // Share the row lock with admin updates/deletion so concurrent reads cannot create orphan markers.
        var ids = jdbc.queryForList("SELECT n.id FROM sys_notice n WHERE " + PUBLISHED + " AND n.id=? FOR UPDATE", Long.class, noticeId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告已下线或已删除");
        jdbc.update("INSERT IGNORE INTO sys_notice_read(notice_id,member_id) VALUES(?,?)", noticeId, memberId);
    }

    public int unreadCount(long memberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice n WHERE " + PUBLISHED
                + " AND NOT EXISTS(SELECT 1 FROM sys_notice_read r WHERE r.notice_id=n.id AND r.member_id=?)",
                Integer.class, memberId);
        return count == null ? 0 : count;
    }
}
