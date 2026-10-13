package com.macro.mall.portal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员收益账本唯一入口（唯一真源：ums_earnings + ums_earnings_ledger）。
 *
 * 推荐奖励、每日分红、分享奖励等一切"给会员加钱"的动作都必须经由此服务，
 * 禁止再直接写第二套账本（历史 member_cash_account/member_cash_ledger 已弃用）。
 * 消费者端的"我的收益/钱包/提现"与后台"佣金"读的都是这套账本。
 */
@Service
public class MemberEarningsCreditService {

    private final JdbcTemplate jdbc;

    public MemberEarningsCreditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等入账：同一 idempotencyKey 只会成功一次。
     *
     * @return true 表示本次真正入账；false 表示金额非法或该幂等键已入账。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean credit(long memberId, BigDecimal amount, String changeType, String bizId,
                          String idempotencyKey, String remark) {
        if (amount == null || amount.signum() <= 0) return false;
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        if (alreadyPosted(idempotencyKey)) return false;

        jdbc.update("INSERT INTO ums_earnings(member_id,total_earnings,available_earnings,"
                + "frozen_earnings,withdrawn,version) VALUES(?,0,0,0,0,0)"
                + " ON DUPLICATE KEY UPDATE member_id=member_id", memberId);

        for (int attempt = 0; attempt < 5; attempt++) {
            Long version = jdbc.queryForObject(
                    "SELECT version FROM ums_earnings WHERE member_id=?", Long.class, memberId);
            BigDecimal available = jdbc.queryForObject(
                    "SELECT available_earnings FROM ums_earnings WHERE member_id=?", BigDecimal.class, memberId);
            BigDecimal total = jdbc.queryForObject(
                    "SELECT total_earnings FROM ums_earnings WHERE member_id=?", BigDecimal.class, memberId);
            if (version == null || available == null || total == null) continue;

            BigDecimal newAvailable = available.add(amount);
            BigDecimal newTotal = total.add(amount);
            int rows = jdbc.update("UPDATE ums_earnings SET total_earnings=?,available_earnings=?,"
                            + "version=version+1 WHERE member_id=? AND version=?",
                    newTotal, newAvailable, memberId, version);
            if (rows > 0) {
                jdbc.update("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,"
                                + "biz_id,idempotency_key,remark) VALUES(?,?,?,?,?,?,?)",
                        memberId, changeType, amount, newAvailable, bizId, idempotencyKey, remark);
                return true;
            }
        }
        throw new IllegalStateException("收益入账并发冲突，请重试");
    }

    /** 已按该幂等键入账过则返回 true。 */
    public boolean alreadyPosted(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return false;
        List<Long> ids = jdbc.query("SELECT id FROM ums_earnings_ledger WHERE idempotency_key=? LIMIT 1",
                (rs, n) -> rs.getLong(1), idempotencyKey);
        return !ids.isEmpty();
    }

    /** 可提现余额（供下单抵扣等场景只读查询）。 */
    public BigDecimal availableBalance(long memberId) {
        List<BigDecimal> rows = jdbc.query("SELECT available_earnings FROM ums_earnings WHERE member_id=?",
                (rs, n) -> rs.getBigDecimal(1), memberId);
        return rows.isEmpty() || rows.get(0) == null ? BigDecimal.ZERO : rows.get(0);
    }
}
