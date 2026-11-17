package com.macro.mall.portal.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.config.WechatMiniProgramProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Keeps provider-specific mini-program attempts tied to the existing mall order. */
@Service
public class WechatMiniPaymentService {
    private final JdbcTemplate jdbc;
    private final WechatMiniProgramProperties properties;
    private final WechatOrderPayService wechat;

    public WechatMiniPaymentService(JdbcTemplate jdbc, WechatMiniProgramProperties properties, WechatOrderPayService wechat) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.wechat = wechat;
    }

    @Transactional
    public Map<String, String> prepay(OmsOrder order, String openId) {
        var amounts = jdbc.queryForList("SELECT pay_amount FROM oms_order WHERE id=? AND status=0 FOR UPDATE",
                BigDecimal.class, order.getId());
        if (amounts.size() != 1 || amounts.get(0).compareTo(order.getPayAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已变化，请刷新后核对支付状态");
        }
        var attempts = jdbc.queryForList("SELECT app_id,open_id,amount,out_trade_no FROM oms_wechat_mini_payment WHERE order_id=?",
                order.getId());
        String tradeNo = tradeNo(properties.getAppId(), order.getOrderSn());
        if (attempts.isEmpty()) {
            jdbc.update("INSERT INTO oms_wechat_mini_payment(order_id,order_sn,out_trade_no,app_id,open_id,amount) VALUES(?,?,?,?,?,?)",
                    order.getId(), order.getOrderSn(), tradeNo, properties.getAppId(), openId, order.getPayAmount());
        } else {
            var attempt = attempts.get(0);
            if (!properties.getAppId().equals(attempt.get("app_id")) || !openId.equals(attempt.get("open_id"))
                    || new BigDecimal(attempt.get("amount").toString()).compareTo(order.getPayAmount()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单的微信付款账号或金额已变化，请查询订单后重试");
            }
            tradeNo = attempt.get("out_trade_no").toString();
        }
        return wechat.prepayMiniProgram(tradeNo, order.getPayAmount(), "商城订单 " + order.getOrderSn(), openId);
    }

    public WechatOrderPayService.PaymentQuery query(OmsOrder order) {
        var attempts = jdbc.queryForList("SELECT out_trade_no FROM oms_wechat_mini_payment WHERE order_id=? AND app_id=?",
                String.class, order.getId(), properties.getAppId());
        if (attempts.isEmpty()) return new WechatOrderPayService.PaymentQuery("NOTPAY", null);
        var result = wechat.queryMiniPayment(attempts.get(0));
        return new WechatOrderPayService.PaymentQuery(result.tradeState(),
                result.payment() == null ? null : originalPayment(result.payment()));
    }

    public WechatOrderPayService.VerifiedPayment originalPayment(WechatOrderPayService.VerifiedPayment payment) {
        var attempts = jdbc.queryForList("SELECT order_sn,amount FROM oms_wechat_mini_payment WHERE out_trade_no=? AND app_id=?",
                payment.outTradeNo(), properties.getAppId());
        if (attempts.size() != 1 || new BigDecimal(attempts.get(0).get("amount").toString()).compareTo(payment.amount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小程序支付订单或金额校验失败");
        }
        return new WechatOrderPayService.VerifiedPayment(attempts.get(0).get("order_sn").toString(),
                payment.transactionId(), payment.amount());
    }

    private String tradeNo(String appId, String orderSn) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((appId + ":" + orderSn).getBytes(StandardCharsets.UTF_8));
            return "MP" + HexFormat.of().formatHex(hash).substring(0, 30);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
