package com.macro.mall.portal.service;

import com.macro.mall.common.exception.Asserts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Creates the account and its optional direct referral in one transaction. */
@Service
public class InviteRegistrationService {
    private final UmsMemberService members;
    private final InvitationService invitations;
    private final JdbcTemplate jdbc;

    public InviteRegistrationService(UmsMemberService members, InvitationService invitations, JdbcTemplate jdbc) {
        this.members = members;
        this.invitations = invitations;
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(String username, String password, String telephone, String authCode, String inviteCode) {
        String normalizedCode = StringUtils.hasText(inviteCode)
                ? inviteCode.trim().toUpperCase(Locale.ROOT) : null;
        if (normalizedCode != null && !normalizedCode.matches("[A-Z0-9]{8}")) {
            Asserts.fail("邀请码格式不正确");
        }
        members.register(username, password, telephone, authCode);
        if (normalizedCode == null) return;

        Long newMemberId = jdbc.queryForObject(
                "SELECT id FROM ums_member WHERE username=? LIMIT 1", Long.class, username);
        var result = invitations.bind(newMemberId, normalizedCode, newMemberId);
        if (result.getCode() != 200) Asserts.fail(result.getMessage());
    }
}
