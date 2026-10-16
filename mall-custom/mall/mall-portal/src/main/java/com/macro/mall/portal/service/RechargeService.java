package com.macro.mall.portal.service;

import com.macro.mall.portal.config.RechargeProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class RechargeService {
    @org.springframework.beans.factory.annotation.Value("${mall.payment.mock.enabled:false}")
    private boolean sandboxEnabled;
    private final JdbcTemplate jdbc;
    private final MembershipPaymentGateway gateway;
    public RechargeService(JdbcTemplate jdbc, RechargeProperties properties) {
        this.jdbc = jdbc;
        this.gateway = new MembershipPaymentGateway(properties.getPayment());
    }
    public MembershipPaymentGateway gateway() { return gateway; }
    public boolean sandboxEnabled() { return sandboxEnabled; }
    public Map<String,Object> paymentOptions() {
        List<String> channels = new ArrayList<>();
        if (sandboxEnabled) channels.add("MOCK");
        else for (String channel : List.of("WECHAT", "ALIPAY")) {
            try { gateway.requireReady(channel); channels.add(channel); }
            catch (com.macro.mall.portal.domain.MembershipPaymentException ignored) { }
        }
        return Map.of("sandbox", sandboxEnabled, "channels", channels);
    }
    private void requireChannel(String channel) {
        if ("MOCK".equals(channel)) {
            if (!sandboxEnabled) throw new IllegalArgumentException("沙盒充值未启用");
        } else {
            if (sandboxEnabled) throw new IllegalArgumentException("沙盒环境请使用模拟充值");
            gateway.requireReady(channel);
        }
    }
    public static long cents(String amount) {
        if (amount == null || !amount.matches("(?:0|[1-9][0-9]{0,4})(?:\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("金额须为最多两位小数的人民币金额");
        long value = new BigDecimal(amount).movePointRight(2).longValueExact();
        if (value < 1 || value > 5000000) throw new IllegalArgumentException("单笔充值范围为0.01至50000.00元");
        return value;
    }
    private static String money(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> create(long member, String amount, String channel, String requestId) {
        long value = cents(amount);
        if (requestId == null || !requestId.matches("[A-Za-z0-9-]{16,64}"))
            throw new IllegalArgumentException("充值请求编号无效");
        requireChannel(channel);
        String no = "RC" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO ums_recharge_order(order_no,member_id,request_id,amount_cents,channel) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE order_no=order_no",
                no, member, requestId, value, channel);
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM ums_recharge_order WHERE member_id=? AND request_id=?", member, requestId);
        if (((Number)row.get("amount_cents")).longValue()!=value || !channel.equals(row.get("channel")))
            throw new IllegalArgumentException("相同请求不能修改充值金额或支付渠道");
        return view(row);
    }
    public Map<String,Object> order(long member, String no) {
        var rows = jdbc.queryForList("SELECT * FROM ums_recharge_order WHERE member_id=? AND order_no=?", member, no);
        if (rows.isEmpty()) throw new IllegalArgumentException("充值订单不存在或无权访问");
        return view(rows.get(0));
    }
    public List<Map<String,Object>> history(long member) {
        return jdbc.queryForList("SELECT * FROM ums_recharge_order WHERE member_id=? ORDER BY created_at DESC,order_no DESC LIMIT 50", member)
                .stream().map(this::view).toList();
    }
    public Map<String,Object> account(long member) {
        List<Long> values = jdbc.query("SELECT balance_cents FROM ums_recharge_account WHERE member_id=?", (rs,n)->rs.getLong(1), member);
        return Map.of("balance", money(values.isEmpty()?0:values.get(0)), "currency", "CNY");
    }

    public List<Map<String,Object>> ledger(long member) {
        return jdbc.queryForList("SELECT change_type,amount_cents,balance_after_cents,remark,create_time FROM ums_recharge_ledger WHERE member_id=? ORDER BY id DESC LIMIT 50",member);
    }

    /** 消费余额（分）。未开户返回 0。 */
    public long balanceCents(long member) {
        List<Long> values = jdbc.query("SELECT balance_cents FROM ums_recharge_account WHERE member_id=?",
                (rs,n)->rs.getLong(1), member);
        return values.isEmpty()?0:values.get(0);
    }

    /**
     * 余额（充值本金）抵扣订单：同一事务内锁定账户、校验余额、扣减并写流水。
     * 同一幂等键重复调用只扣减一次，返回扣减后余额。
     */
    @Transactional(rollbackFor = Exception.class)
    public long spendBalance(long member, long cents, String bizId, String idempotencyKey, String remark) {
        validateBalanceRequest(cents, bizId, idempotencyKey);
        long balance = lockBalance(member);
        Long replay = balanceReplay(member, -cents, bizId, idempotencyKey, "order_pay");
        if (replay != null) return replay;
        if (balance < cents) throw new IllegalArgumentException("余额不足，无法用余额支付本专区商品");
        long next = Math.subtractExact(balance, cents);
        jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?", next, member);
        recordLedger(member, "order_pay", -cents, next, bizId, idempotencyKey, remark);
        return next;
    }

    /** 仅可退还同一会员、同一业务单真实扣减的本金，累计退款不能超过原扣减。 */
    @Transactional(rollbackFor = Exception.class)
    public void refundBalance(long member, long cents, String bizId, String idempotencyKey, String remark) {
        if (cents <= 0) return;
        validateBalanceRequest(cents, bizId, idempotencyKey);
        long balance = lockBalance(member);
        if (balanceReplay(member, cents, bizId, idempotencyKey, "order_refund") != null) return;
        // Locking read sees committed ledger rows even under MySQL REPEATABLE READ.
        List<Map<String,Object>> ledger = jdbc.queryForList("SELECT change_type,amount_cents FROM ums_recharge_ledger WHERE member_id=? AND biz_id=? AND change_type IN ('order_pay','order_refund') FOR UPDATE", member, bizId);
        long paid = 0, returned = 0;
        for (Map<String,Object> row : ledger) {
            long value = ((Number)row.get("amount_cents")).longValue();
            if ("order_pay".equals(row.get("change_type"))) paid = Math.subtractExact(paid, value);
            else returned = Math.addExact(returned, value);
        }
        if (cents > Math.subtractExact(paid, returned)) throw new IllegalArgumentException("余额退款超过该会员订单实际扣减的未退本金");
        long next = Math.addExact(balance, cents);
        jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?", next, member);
        recordLedger(member, "order_refund", cents, next, bizId, idempotencyKey, remark);
    }

    private void validateBalanceRequest(long cents, String bizId, String key) {
        if (cents <= 0) throw new IllegalArgumentException("余额变动金额必须大于0");
        if (bizId == null || bizId.isBlank() || bizId.length() > 64) throw new IllegalArgumentException("余额业务单号无效");
        if (key == null || !key.matches("[A-Za-z0-9:_-]{8,64}")) throw new IllegalArgumentException("余额请求编号无效");
    }

    private long lockBalance(long member) {
        jdbc.update("INSERT INTO ums_recharge_account(member_id,balance_cents) VALUES(?,0) ON DUPLICATE KEY UPDATE member_id=member_id", member);
        return jdbc.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=? FOR UPDATE", Long.class, member);
    }

    private Long balanceReplay(long member, long signedCents, String bizId, String key, String type) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT member_id,change_type,amount_cents,biz_id,balance_after_cents FROM ums_recharge_ledger WHERE idempotency_key=? FOR UPDATE", key);
        if (rows.isEmpty()) return null;
        Map<String,Object> row = rows.get(0);
        if (((Number)row.get("member_id")).longValue() != member || ((Number)row.get("amount_cents")).longValue() != signedCents
                || !bizId.equals(row.get("biz_id")) || !type.equals(row.get("change_type"))) {
            throw new IllegalArgumentException("同一余额请求编号不可修改会员、金额或业务单号");
        }
        return ((Number)row.get("balance_after_cents")).longValue();
    }

    private void recordLedger(long member, String changeType, long amountCents, long balanceAfter,
                              String bizId, String idempotencyKey, String remark) {
        jdbc.update("INSERT INTO ums_recharge_ledger"
                        + "(member_id,change_type,amount_cents,balance_after_cents,biz_id,idempotency_key,remark)"
                        + " VALUES(?,?,?,?,?,?,?)",
                member, changeType, amountCents, balanceAfter, bizId, idempotencyKey, remark);
    }
    public Map<String,String> pay(long member, String no, String ip) {
        Map<String,Object> row = order(member,no);
        if (!"PENDING".equals(row.get("status"))) throw new IllegalArgumentException("订单已到账，请勿重复付款");
        String channel = (String)row.get("channel");
        requireChannel(channel);
        if ("MOCK".equals(channel)) return Map.of("type", "SANDBOX_RECHARGE", "orderNo", no);
        return gateway.start(no,"RECHARGE",new BigDecimal((String)row.get("amount")),channel,
                "WECHAT".equals(channel)?"WECHAT_H5":"ALIPAY_WAP",null,ip);
    }
    /** Only the explicit sandbox channel may use a server-generated mock receipt. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> confirmSandbox(long member, String no) {
        if (!sandboxEnabled) throw new IllegalArgumentException("沙盒充值未启用");
        var rows = jdbc.queryForList("SELECT * FROM ums_recharge_order WHERE member_id=? AND order_no=? FOR UPDATE", member, no);
        if (rows.isEmpty()) throw new IllegalArgumentException("充值订单不存在或无权访问");
        var row = rows.get(0);
        if (!"MOCK".equals(row.get("channel"))) throw new IllegalArgumentException("真实支付订单不能模拟到账");
        if (!"PAID".equals(row.get("status"))) {
            long value = ((Number)row.get("amount_cents")).longValue();
            accept(new MembershipPaymentGateway.VerifiedPayment(no, "SANDBOX-"+no, "MOCK", BigDecimal.valueOf(value,2)));
            recordLedger(member, "sandbox_recharge", value, balanceCents(member), no, "sandbox:"+no, "沙盒模拟充值，不发生真实收款");
        }
        return order(member,no);
    }
    /** Credited only by the transactional sandbox withdrawal service, never directly by a client. */
    @Transactional(rollbackFor = Exception.class)
    public void creditSandboxWithdrawal(long member, long cents, long withdrawalId) {
        if (!sandboxEnabled) throw new IllegalArgumentException("沙盒提现未启用");
        String biz = "WITHDRAW:"+withdrawalId, key = "sandbox:"+biz;
        if (cents < 0) throw new IllegalArgumentException("到账金额无效");
        long balance = lockBalance(member);
        if (balanceReplay(member,cents,biz,key,"sandbox_withdrawal") != null) return;
        long next = Math.addExact(balance,cents);
        jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?",next,member);
        recordLedger(member,"sandbox_withdrawal",cents,next,biz,key,"沙盒收益提现转入消费余额");
    }
    /** Only verified provider notifications may call this method. Row lock serializes retries. */
    @Transactional(rollbackFor = Exception.class)
    public void accept(MembershipPaymentGateway.VerifiedPayment payment) {
        if (payment == null || payment.transactionId() == null || payment.transactionId().isBlank()
                || payment.transactionId().length()>128 || payment.amount()==null)
            throw new IllegalArgumentException("无效支付通知");
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM ums_recharge_order WHERE order_no=? FOR UPDATE",payment.merchantOrderNo());
        long expected = ((Number)row.get("amount_cents")).longValue();
        if (!payment.channel().equals(row.get("channel")) || payment.amount().movePointRight(2).longValueExact()!=expected)
            throw new IllegalArgumentException("实付金额或渠道与充值订单不符");
        if ("PAID".equals(row.get("status"))) {
            if (!payment.transactionId().equals(row.get("transaction_id"))) throw new IllegalArgumentException("重复支付交易号不符");
            return;
        }
        if (!"PENDING".equals(row.get("status"))) throw new IllegalArgumentException("订单状态异常");
        long member = ((Number)row.get("member_id")).longValue();
        jdbc.update("INSERT INTO ums_recharge_account(member_id,balance_cents) VALUES(?,0) ON DUPLICATE KEY UPDATE member_id=member_id",member);
        long balance = jdbc.queryForObject("SELECT balance_cents FROM ums_recharge_account WHERE member_id=? FOR UPDATE",Long.class,member);
        long next = Math.addExact(balance,expected);
        jdbc.update("UPDATE ums_recharge_account SET balance_cents=? WHERE member_id=?",next,member);
        jdbc.update("UPDATE ums_recharge_order SET status='PAID',transaction_id=?,balance_after_cents=?,paid_at=CURRENT_TIMESTAMP WHERE order_no=?",
                payment.transactionId(),next,payment.merchantOrderNo());
    }
    private Map<String,Object> view(Map<String,Object> row) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("orderNo",row.get("order_no")); result.put("amount",money(((Number)row.get("amount_cents")).longValue()));
        result.put("channel",row.get("channel")); result.put("status",row.get("status"));
        result.put("createdAt",row.get("created_at")); result.put("paidAt",row.get("paid_at"));
        return result;
    }
}
