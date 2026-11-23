package com.macro.mall.portal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** One atomic setting shared by earnings transfers and balance withdrawals. */
@Service
public class WithdrawalPolicy {
    private final JdbcTemplate jdbc;
    public WithdrawalPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Rule(String mode, BigDecimal minimum) {
        public String violation(BigDecimal amount) {
            if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2)
                return "请输入大于0、最多两位小数的提现金额";
            if (amount.compareTo(minimum) < 0)
                return "当前提现门槛为" + minimum.stripTrailingZeros().toPlainString() + "元，提现金额未达到门槛";
            if ("HUNDRED".equals(mode) && amount.remainder(new BigDecimal("100")).signum() != 0)
                return "当前提现门槛为100元，金额须为100元的整数倍";
            if (amount.compareTo(new BigDecimal("50000")) > 0) return "单笔提现金额不得超过50000元";
            return null;
        }
    }

    public Rule current() {
        List<String> values = jdbc.query("SELECT config_value FROM sys_config WHERE config_key='withdraw.threshold_rule' LIMIT 1", (rs,n)->rs.getString(1));
        String value = values.isEmpty() || values.get(0) == null ? "HUNDRED" : values.get(0);
        if ("HUNDRED".equals(value)) return new Rule("HUNDRED", new BigDecimal("100"));
        if ("NONE".equals(value)) return new Rule("NONE", BigDecimal.ZERO);
        if (value.matches("CUSTOM:(?:0|[1-9][0-9]{0,4})(?:\\.[0-9]{1,2})?")) {
            BigDecimal minimum = new BigDecimal(value.substring(7));
            if (minimum.signum() > 0 && minimum.compareTo(new BigDecimal("50000")) <= 0) return new Rule("CUSTOM", minimum);
        }
        throw new IllegalArgumentException("提现门槛配置无效，请联系管理员");
    }

    public Map<String,Object> options() {
        Rule rule = current();
        List<String> rates = jdbc.query("SELECT config_value FROM sys_config WHERE config_key='withdraw.fee_rate' LIMIT 1", (rs,n)->rs.getString(1));
        BigDecimal rate = rates.isEmpty() || rates.get(0) == null ? new BigDecimal("0.006") : new BigDecimal(rates.get(0));
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("提现手续费率配置无效");
        return Map.of("mode", rule.mode(), "minimum", rule.minimum(), "maximum", 50000, "feeRate", rate);
    }
}
