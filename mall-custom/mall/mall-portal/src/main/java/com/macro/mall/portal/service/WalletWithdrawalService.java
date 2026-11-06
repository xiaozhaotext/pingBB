package com.macro.mall.portal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class WalletWithdrawalService {
    private final JdbcTemplate jdbc;
    public WalletWithdrawalService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Request(String amount, String requestId, String recipientName, String channel,
                          String bankName, String bankBranch, String recipientAccount) {}

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> apply(long member, Request request) {
        if (request == null || request.amount() == null || !request.amount().matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("提现金额必须为100元的整数倍");
        BigDecimal yuan = new BigDecimal(request.amount());
        if (yuan.compareTo(new BigDecimal("100")) < 0 || yuan.compareTo(new BigDecimal("50000")) > 0
                || yuan.remainder(new BigDecimal("100")).signum() != 0)
            throw new IllegalArgumentException("单笔提现须为100至50000元之间的整百金额");
        if (request.requestId() == null || !request.requestId().matches("[A-Za-z0-9_-]{16,64}"))
            throw new IllegalArgumentException("提现请求编号无效");
        String name = clean(request.recipientName(), 2, 80, "真实姓名");
        String channel = request.channel();
        if (channel == null || !List.of("WECHAT", "ALIPAY", "BANK").contains(channel))
            throw new IllegalArgumentException("收款方式无效");
        String account = clean(request.recipientAccount(), 4, 160, "收款账户");
        String bank = "BANK".equals(channel) ? clean(request.bankName(), 2, 120, "开户银行") : null;
        String branch = "BANK".equals(channel) ? clean(request.bankBranch(), 2, 160, "收款支行") : null;
        if ("BANK".equals(channel) && !account.matches("[0-9]{12,30}"))
            throw new IllegalArgumentException("银行卡号须为12至30位数字");
        String requestId = request.requestId();
        // Lock the member before the wallet so retries and concurrent applications serialize.
        List<Long> members = jdbc.query("SELECT id FROM ums_member WHERE id=? FOR UPDATE", (rs, n)->rs.getLong(1), member);
        if (members.isEmpty()) throw new IllegalArgumentException("会员不存在");
        List<Map<String,Object>> previous = jdbc.queryForList("SELECT id,gross_cents,recipient_name,channel,bank_name,bank_branch,recipient_account FROM ums_wallet_withdrawal WHERE member_id=? AND request_id=?", member, requestId);
        long cents = yuan.movePointRight(2).longValueExact();
        if (!previous.isEmpty()) {
            Map<String,Object> old = previous.get(0);
            if (((Number)old.get("gross_cents")).longValue()!=cents || !name.equals(old.get("recipient_name"))
                    || !channel.equals(old.get("channel")) || !account.equals(old.get("recipient_account"))
                    || !java.util.Objects.equals(bank,old.get("bank_name")) || !java.util.Objects.equals(branch,old.get("bank_branch")))
                throw new IllegalArgumentException("同一请求编号不可更改提现信息");
            return view(member, ((Number)old.get("id")).longValue());
        }
        jdbc.update("INSERT INTO ums_recharge_account(member_id,balance_cents) VALUES(?,0) ON DUPLICATE KEY UPDATE member_id=member_id",member);
        long balance = jdbc.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=? FOR UPDATE",Long.class,member);
        if (balance < cents) throw new IllegalArgumentException("账户余额不足");
        BigDecimal rate = feeRate();
        long fee = BigDecimal.valueOf(cents).multiply(rate).setScale(0,RoundingMode.HALF_UP).longValueExact();
        long next = balance - cents;
        jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?",next,member);
        jdbc.update("INSERT INTO ums_wallet_withdrawal(member_id,request_id,gross_cents,fee_cents,net_cents,fee_rate,recipient_name,channel,bank_name,bank_branch,recipient_account) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                member,requestId,cents,fee,cents-fee,rate,name,channel,bank,branch,account);
        long id = jdbc.queryForObject("SELECT id FROM ums_wallet_withdrawal WHERE member_id=? AND request_id=?",Long.class,member,requestId);
        jdbc.update("INSERT INTO ums_recharge_ledger(member_id,change_type,amount_cents,balance_after_cents,biz_id,idempotency_key,remark) VALUES(?,'wallet_withdraw',?,?,?,?,?)",
                member,-cents,next,"WITHDRAW:"+id,"withdraw:"+id,"余额提现申请，待人工打款");
        return view(member,id);
    }

    public List<Map<String,Object>> list(long member) {
        return jdbc.queryForList("SELECT id,gross_cents,fee_cents,net_cents,fee_rate,recipient_name,channel,bank_name,bank_branch,status,decision_note,payout_reference,created_at,decided_at FROM ums_wallet_withdrawal WHERE member_id=? ORDER BY id DESC LIMIT 50",member);
    }
    private Map<String,Object> view(long member,long id) {
        return jdbc.queryForMap("SELECT id,gross_cents,fee_cents,net_cents,fee_rate,recipient_name,channel,bank_name,bank_branch,status,decision_note,payout_reference,created_at,decided_at FROM ums_wallet_withdrawal WHERE member_id=? AND id=?",member,id);
    }
    private BigDecimal feeRate() {
        List<String> values = jdbc.query("SELECT config_value FROM sys_config WHERE config_key='withdraw.fee_rate' LIMIT 1",(rs,n)->rs.getString(1));
        if (values.isEmpty() || values.get(0)==null) return new BigDecimal("0.006");
        BigDecimal rate = new BigDecimal(values.get(0));
        if (rate.signum()<0 || rate.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("后台提现手续费率配置无效");
        return rate.setScale(6,RoundingMode.HALF_UP);
    }
    private static String clean(String value,int min,int max,String label) {
        String text = value==null?"":value.trim();
        if (text.length()<min || text.length()>max || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label+"格式无效");
        return text;
    }
}
