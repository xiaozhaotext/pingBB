package com.macro.mall.portal.service;

import com.macro.mall.portal.config.RechargeProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class RechargeService {
    private final JdbcTemplate jdbc;
    private final MembershipPaymentGateway gateway;
    public RechargeService(JdbcTemplate jdbc, RechargeProperties properties) {
        this.jdbc = jdbc;
        this.gateway = new MembershipPaymentGateway(properties.getPayment());
    }
    public MembershipPaymentGateway gateway() { return gateway; }
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
        gateway.requireReady(channel);
        String no = "RC" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO ums_recharge_order(order_no,member_id,request_id,amount_cents,channel) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE order_no=order_no",
                no, member, requestId, value, channel);
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM ums_recharge_order WHERE member_id=? AND request_id=?", member, requestId);
        if (((Number)row.get("amount_cents")).longValue()!=value || !channel.equals(row.get("channel")))
            throw new IllegalArgumentException("相同请求不能修改充值金额或支付渠道");
        return view(row);
    }
    public Map<String,Object> order(long member, String no) {
        return view(jdbc.queryForMap("SELECT * FROM ums_recharge_order WHERE member_id=? AND order_no=?", member, no));
    }
    public List<Map<String,Object>> history(long member) {
        return jdbc.queryForList("SELECT * FROM ums_recharge_order WHERE member_id=? ORDER BY created_at DESC,order_no DESC LIMIT 50", member)
                .stream().map(this::view).toList();
    }
    public Map<String,Object> account(long member) {
        List<Long> values = jdbc.query("SELECT balance_cents FROM ums_recharge_account WHERE member_id=?", (rs,n)->rs.getLong(1), member);
        return Map.of("balance", money(values.isEmpty()?0:values.get(0)), "currency", "CNY");
    }
    public Map<String,String> pay(long member, String no, String ip) {
        Map<String,Object> row = order(member,no);
        if (!"PENDING".equals(row.get("status"))) throw new IllegalArgumentException("订单已到账，请勿重复付款");
        String channel = (String)row.get("channel");
        return gateway.start(no,"RECHARGE",new BigDecimal((String)row.get("amount")),channel,
                "WECHAT".equals(channel)?"WECHAT_H5":"ALIPAY_WAP",null,ip);
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
