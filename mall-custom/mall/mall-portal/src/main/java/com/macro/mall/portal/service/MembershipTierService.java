package com.macro.mall.portal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads tier inputs from verified membership orders, never from registrations alone. */
@Service
public class MembershipTierService {
    private final JdbcTemplate jdbc;

    public MembershipTierService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public MembershipTierRules.Tier tier(long memberId) {
        return MembershipTierRules.forMember(isPaidMember(memberId), paidDirectReferrals(memberId));
    }

    public int paidDirectReferrals(long memberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(DISTINCT r.member_id) FROM ums_member_recommend r"
                + " JOIN membership_purchase_order o ON o.member_id=r.member_id"
                + " AND o.level='PARTNER' AND o.status='PAID' AND o.amount=3999.00"
                + " WHERE r.inviter_id=? AND EXISTS (SELECT 1 FROM membership_purchase_order inviter"
                + " WHERE inviter.member_id=r.inviter_id AND inviter.level='PARTNER'"
                + " AND inviter.status='PAID' AND inviter.amount=3999.00"
                + " AND inviter.paid_at<=r.bind_time)", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public boolean isPaidMember(long memberId) {
        Integer paid = jdbc.queryForObject("SELECT COUNT(*) FROM membership_purchase_order"
                + " WHERE member_id=? AND level='PARTNER' AND status='PAID' AND amount=3999.00",
                Integer.class, memberId);
        return paid != null && paid > 0;
    }
}
