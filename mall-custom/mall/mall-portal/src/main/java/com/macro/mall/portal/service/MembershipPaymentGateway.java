package com.macro.mall.portal.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.h5.model.SceneInfo;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.macro.mall.portal.config.MembershipPaymentProperties;
import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The only channel adapter for real membership purchases; never fabricates a provider response. */
@Service
public class MembershipPaymentGateway {
    private final MembershipPaymentProperties properties;
    private volatile RSAAutoCertificateConfig wechatConfig;

    public MembershipPaymentGateway(MembershipPaymentProperties properties) {
        this.properties = properties;
    }

    public record VerifiedPayment(String merchantOrderNo, String transactionId,
                                  String channel, BigDecimal amount) {}

    /** Authenticated server-to-server lookup, used to recover missing notifications. */
    public VerifiedPayment query(String no, String channel) {
        if ("ALIPAY".equals(channel)) {
            var config = requireAlipay();
            try {
                AlipayClient client = new DefaultAlipayClient(config.getGatewayUrl(), config.getAppId(),
                        readKey(config.getPrivateKeyPath()), "JSON", "UTF-8", readKey(config.getPublicKeyPath()), "RSA2");
                var request = new com.alipay.api.request.AlipayTradeQueryRequest();
                var body = new com.alibaba.fastjson.JSONObject();
                body.put("out_trade_no", no);
                request.setBizContent(body.toString());
                var response = client.execute(request);
                if (!response.isSuccess()) throw new MembershipPaymentException(MembershipPaymentError.PROVIDER_ERROR);
                if (!("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus()))) return null;
                if (!no.equals(response.getOutTradeNo())) throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
                return new VerifiedPayment(no,response.getTradeNo(),channel,new BigDecimal(response.getTotalAmount()));
            } catch (AlipayApiException ex) { throw new MembershipPaymentException(MembershipPaymentError.PROVIDER_ERROR); }
        }
        requireWechat();
        var request = new com.wechat.pay.java.service.payments.h5.model.QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getWechat().getMerchantId());
        request.setOutTradeNo(no);
        Transaction transaction = new H5Service.Builder().config(wechatConfig()).build().queryOrderByOutTradeNo(request);
        if (transaction.getTradeState()!=Transaction.TradeStateEnum.SUCCESS) return null;
        if (!no.equals(transaction.getOutTradeNo()) || !properties.getWechat().getAppId().equals(transaction.getAppid())
                || !properties.getWechat().getMerchantId().equals(transaction.getMchid())
                || transaction.getAmount()==null || !"CNY".equals(transaction.getAmount().getCurrency()))
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        return new VerifiedPayment(no,transaction.getTransactionId(),channel,BigDecimal.valueOf(transaction.getAmount().getTotal(),2));
    }

    public void requireReady(String channel) {
        if ("ALIPAY".equals(channel)) requireAlipay();
        else if ("WECHAT".equals(channel)) requireWechat();
        else throw new MembershipPaymentException(MembershipPaymentError.INVALID_REQUEST);
    }

    public Map<String, String> start(String merchantOrderNo, String level, BigDecimal amount,
                                     String channel, String mode, String openId, String clientIp) {
        if ("ALIPAY".equals(channel) && "ALIPAY_WAP".equals(mode)) {
            return startAlipay(merchantOrderNo, level, amount);
        }
        if ("WECHAT".equals(channel) && "WECHAT_H5".equals(mode)) {
            return startWechatH5(merchantOrderNo, level, amount, clientIp);
        }
        if ("WECHAT".equals(channel) && "WECHAT_JSAPI".equals(mode) && StringUtils.hasText(openId)) {
            return startWechatJsapi(merchantOrderNo, level, amount, openId);
        }
        throw new MembershipPaymentException(MembershipPaymentError.INVALID_REQUEST);
    }

    public VerifiedPayment verifyAlipay(Map<String, String> params) {
        MembershipPaymentProperties.Alipay config = requireAlipay();
        try {
            boolean valid = AlipaySignature.rsaCheckV1(params, readKey(config.getPublicKeyPath()), "UTF-8", "RSA2");
            if (!valid || !config.getAppId().equals(params.get("app_id"))
                    || !config.getSellerId().equals(params.get("seller_id"))
                    || (StringUtils.hasText(params.get("currency")) && !"CNY".equals(params.get("currency")))
                    || !("TRADE_SUCCESS".equals(params.get("trade_status")) || "TRADE_FINISHED".equals(params.get("trade_status")))
                    || !StringUtils.hasText(params.get("out_trade_no"))
                    || !StringUtils.hasText(params.get("trade_no"))) {
                throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
            }
            BigDecimal paid = new BigDecimal(params.get("total_amount"));
            if (paid.signum() <= 0) throw new NumberFormatException("amount");
            return new VerifiedPayment(params.get("out_trade_no"), params.get("trade_no"), "ALIPAY", paid);
        } catch (AlipayApiException | NumberFormatException e) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        }
    }

    public VerifiedPayment verifyWechat(String rawBody, String serial, String nonce,
                                        String signature, String timestamp) {
        MembershipPaymentProperties.Wechat config = requireWechat();
        try {
            RequestParam param = new RequestParam.Builder()
                    .body(rawBody).serialNumber(serial).nonce(nonce)
                    .signature(signature).timestamp(timestamp).build();
            Transaction transaction = new NotificationParser(wechatConfig()).parse(param, Transaction.class);
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS
                    || !config.getMerchantId().equals(transaction.getMchid())
                    || !config.getAppId().equals(transaction.getAppid())
                    || transaction.getAmount() == null
                    || !"CNY".equals(transaction.getAmount().getCurrency())
                    || transaction.getAmount().getTotal() == null
                    || transaction.getAmount().getTotal() <= 0
                    || !StringUtils.hasText(transaction.getOutTradeNo())
                    || !StringUtils.hasText(transaction.getTransactionId())) {
                throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
            }
            return new VerifiedPayment(transaction.getOutTradeNo(), transaction.getTransactionId(),
                    "WECHAT", BigDecimal.valueOf(transaction.getAmount().getTotal(), 2));
        } catch (MembershipPaymentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MembershipPaymentException(MembershipPaymentError.INVALID_NOTIFICATION);
        }
    }

    private Map<String, String> startAlipay(String no, String level, BigDecimal amount) {
        MembershipPaymentProperties.Alipay config = requireAlipay();
        try {
            AlipayClient client = new DefaultAlipayClient(config.getGatewayUrl(), config.getAppId(),
                    readKey(config.getPrivateKeyPath()), "JSON", "UTF-8", readKey(config.getPublicKeyPath()), "RSA2");
            AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
            request.setNotifyUrl(config.getNotifyUrl());
            if (StringUtils.hasText(config.getReturnUrl())) request.setReturnUrl(config.getReturnUrl());
            com.alibaba.fastjson.JSONObject body = new com.alibaba.fastjson.JSONObject();
            body.put("out_trade_no", no);
            body.put("total_amount", amount);
            body.put("subject", "RECHARGE".equals(level) ? "账户充值" : ("MEMBER".equals(level) ? "会员身份" : ("PARTNER".equals(level) ? "合伙人权益" : "联创权益")));
            body.put("product_code", "QUICK_WAP_WAY");
            request.setBizContent(body.toString());
            String form = client.pageExecute(request).getBody();
            if (!StringUtils.hasText(form)) throw new MembershipPaymentException(MembershipPaymentError.PROVIDER_ERROR);
            return Map.of("type", "ALIPAY_FORM", "formHtml", form);
        } catch (AlipayApiException e) {
            throw new MembershipPaymentException(MembershipPaymentError.PROVIDER_ERROR);
        }
    }

    private Map<String, String> startWechatH5(String no, String level, BigDecimal amount, String clientIp) {
        MembershipPaymentProperties.Wechat config = requireWechat();
        if (!StringUtils.hasText(clientIp)) throw new MembershipPaymentException(MembershipPaymentError.INVALID_REQUEST);
        com.wechat.pay.java.service.payments.h5.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        request.setAppid(config.getAppId());
        request.setMchid(config.getMerchantId());
        request.setDescription("RECHARGE".equals(level) ? "账户充值" : ("MEMBER".equals(level) ? "会员身份" : ("PARTNER".equals(level) ? "合伙人权益" : "联创权益")));
        request.setOutTradeNo(no);
        request.setNotifyUrl(config.getNotifyUrl());
        com.wechat.pay.java.service.payments.h5.model.Amount payAmount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
        payAmount.setTotal(amount.movePointRight(2).intValueExact());
        payAmount.setCurrency("CNY");
        request.setAmount(payAmount);
        SceneInfo scene = new SceneInfo();
        scene.setPayerClientIp(clientIp);
        H5Info h5 = new H5Info();
        h5.setType("Wap");
        scene.setH5Info(h5);
        request.setSceneInfo(scene);
        String url = new H5Service.Builder().config(wechatConfig()).build().prepay(request).getH5Url();
        if (!StringUtils.hasText(url)) throw new MembershipPaymentException(MembershipPaymentError.PROVIDER_ERROR);
        return Map.of("type", "WECHAT_H5_URL", "h5Url", url);
    }

    private Map<String, String> startWechatJsapi(String no, String level, BigDecimal amount, String openId) {
        MembershipPaymentProperties.Wechat config = requireWechat();
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        request.setAppid(config.getAppId());
        request.setMchid(config.getMerchantId());
        request.setDescription("RECHARGE".equals(level) ? "账户充值" : ("MEMBER".equals(level) ? "会员身份" : ("PARTNER".equals(level) ? "合伙人权益" : "联创权益")));
        request.setOutTradeNo(no);
        request.setNotifyUrl(config.getNotifyUrl());
        com.wechat.pay.java.service.payments.jsapi.model.Amount payAmount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        payAmount.setTotal(amount.movePointRight(2).intValueExact());
        payAmount.setCurrency("CNY");
        request.setAmount(payAmount);
        Payer payer = new Payer();
        payer.setOpenid(openId);
        request.setPayer(payer);
        PrepayWithRequestPaymentResponse response = new JsapiServiceExtension.Builder()
                .config(wechatConfig()).build().prepayWithRequestPayment(request);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", "WECHAT_JSAPI");
        values.put("appId", response.getAppId());
        values.put("timeStamp", response.getTimeStamp());
        values.put("nonceStr", response.getNonceStr());
        values.put("package", response.getPackageVal());
        values.put("signType", response.getSignType());
        values.put("paySign", response.getPaySign());
        return values;
    }

    private MembershipPaymentProperties.Alipay requireAlipay() {
        MembershipPaymentProperties.Alipay p = properties.getAlipay();
        if (!properties.isEnabled() || !p.isEnabled() || !StringUtils.hasText(p.getAppId())
                || !StringUtils.hasText(p.getSellerId()) || !StringUtils.hasText(p.getGatewayUrl())
                || !StringUtils.hasText(p.getNotifyUrl()) || !fileExists(p.getPrivateKeyPath())
                || !fileExists(p.getPublicKeyPath())) {
            throw new MembershipPaymentException(MembershipPaymentError.CHANNEL_UNAVAILABLE);
        }
        return p;
    }

    private MembershipPaymentProperties.Wechat requireWechat() {
        MembershipPaymentProperties.Wechat p = properties.getWechat();
        if (!properties.isEnabled() || !p.isEnabled() || !StringUtils.hasText(p.getAppId())
                || !StringUtils.hasText(p.getMerchantId()) || !StringUtils.hasText(p.getMerchantSerialNumber())
                || !StringUtils.hasText(p.getApiV3Key()) || !StringUtils.hasText(p.getNotifyUrl())
                || !fileExists(p.getPrivateKeyPath())) {
            throw new MembershipPaymentException(MembershipPaymentError.CHANNEL_UNAVAILABLE);
        }
        return p;
    }

    private RSAAutoCertificateConfig wechatConfig() {
        RSAAutoCertificateConfig current = wechatConfig;
        if (current != null) return current;
        synchronized (this) {
            if (wechatConfig == null) {
                MembershipPaymentProperties.Wechat p = requireWechat();
                wechatConfig = new RSAAutoCertificateConfig.Builder()
                        .merchantId(p.getMerchantId()).privateKeyFromPath(p.getPrivateKeyPath())
                        .merchantSerialNumber(p.getMerchantSerialNumber()).apiV3Key(p.getApiV3Key()).build();
            }
            return wechatConfig;
        }
    }

    private boolean fileExists(String path) {
        return StringUtils.hasText(path) && Files.isRegularFile(Path.of(path));
    }

    private String readKey(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8)
                    .replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        } catch (Exception e) {
            throw new MembershipPaymentException(MembershipPaymentError.CHANNEL_UNAVAILABLE);
        }
    }
}
