package com.macro.mall.portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分享下单奖励：订单支付成功后，给下单人的直推分享人发放固定奖励。
 *
 * 规则全部来自后台配置，默认值仅用于本地闭环演示：
 * - share.order.bonus.enabled：开关（默认关闭，需业务规则确认后由管理员开启）
 * - share.order.bonus：每单奖励金额（默认 200 元）
 * 幂等：同一订单只会发放一次（idempotencyKey = share:order:{orderId}）。
 */
@Service
public class ShareOrderBonusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareOrderBonusService.class);

    private final JdbcTemplate jdbc;
    private final MemberEarningsCreditService earningsCreditService;

    @Value("${mall.share.order.bonus.payout.enabled:false}")
    private boolean payoutEnabled;

    public ShareOrderBonusService(JdbcTemplate jdbc, MemberEarningsCreditService earningsCreditService) {
        this.jdbc = jdbc;
        this.earningsCreditService = earningsCreditService;
    }

    /** @return 实际发放给分享人的金额，未发放时返回 0。 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal postForOrder(long orderId, long buyerMemberId, BigDecimal paidAmount) {
        if (!payoutEnabled) return BigDecimal.ZERO;
        if (!configBool("share.order.bonus.enabled", false)) return BigDecimal.ZERO;
        BigDecimal bonus = configDecimal("share.order.bonus", new BigDecimal("200.00"));
        if (bonus.signum() <= 0) return BigDecimal.ZERO;

        List<Long> referrers = jdbc.query(
                "SELECT inviter_id FROM ums_member_recommend WHERE member_id=? LIMIT 1",
                (rs, n) -> rs.getLong(1), buyerMemberId);
        if (referrers.isEmpty()) return BigDecimal.ZERO;
        long referrerId = referrers.get(0);
        if (referrerId == buyerMemberId) return BigDecimal.ZERO;

        String idempotencyKey = "share:order:" + orderId;
        boolean posted = earningsCreditService.credit(referrerId, bonus, "share_bonus",
                Long.toString(orderId), idempotencyKey,
                "\u5206\u4eab\u4e0b\u5355\u5956\u52b1\uff08\u8ba2\u5355 " + orderId + "\uff09");
        if (!posted) return BigDecimal.ZERO;

        try {
            jdbc.update("INSERT INTO sys_audit_log(operator_name,module,action,target_id,detail,create_time,"
                            + "`operator`,target_type,old_value,new_value,created_at)"
                            + " VALUES('SYSTEM:payment','membership','SHARE_ORDER_BONUS',?,?,CURRENT_TIMESTAMP,"
                            + "'SYSTEM:payment','oms_order',?,?,CURRENT_TIMESTAMP)",
                    orderId,
                    "\u4e0b\u5355\u4eba " + buyerMemberId + " \u652f\u4ed8\u8ba2\u5355 " + orderId
                            + "\uff08\u91d1\u989d " + paidAmount + "\uff09\uff0c\u5206\u4eab\u4eba " + referrerId
                            + " \u83b7\u5f97\u5956\u52b1 " + bonus,
                    "bonus=0", "bonus=" + bonus);
        } catch (Exception ex) {
            // 审计写入失败不应影响订单支付与奖励入账
            LOGGER.warn("share order bonus audit failed, orderId={}", orderId, ex);
        }
        return bonus;
    }

    private boolean configBool(String key, boolean fallback) {
        try {
            String raw = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?", String.class, key);
            if (raw != null) {
                String v = raw.trim();
                if ("1".equals(v) || "true".equalsIgnoreCase(v)) return true;
                if ("0".equals(v) || "false".equalsIgnoreCase(v)) return false;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private BigDecimal configDecimal(String key, BigDecimal fallback) {
        try {
            String raw = jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key=?", String.class, key);
            if (raw != null && !raw.isBlank() && !"NULL".equalsIgnoreCase(raw.trim())) {
                BigDecimal v = new BigDecimal(raw.trim());
                if (v.signum() >= 0) return v;
            }
        } catch (Exception ignored) { }
        return fallback;
    }
}
