package com.macro.mall.portal.service.impl;

import com.macro.mall.portal.config.OrderWechatPayProperties;
import com.macro.mall.portal.config.WechatMiniProgramProperties;
import com.macro.mall.portal.config.WechatSdkConfigFactory;
import com.macro.mall.portal.service.WechatOrderPayService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.h5.model.SceneInfo;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付普通订单实现（H5 / JSAPI / 结果通知验签），与会员支付网关同源，避免两套口径。
 */
@Slf4j
@Service
public class WechatOrderPayServiceImpl implements WechatOrderPayService {

    @Autowired
    private OrderWechatPayProperties properties;

    @Autowired
    private WechatMiniProgramProperties miniProperties;

    private volatile Config wechatConfig;

    @Override
    public PaymentQuery queryPayment(String outTradeNo) {
        return queryForApp(outTradeNo, requireReady().getAppId());
    }

    @Override
    public PaymentQuery queryMiniPayment(String outTradeNo) {
        requireMiniReady();
        return queryForApp(outTradeNo, miniProperties.getAppId());
    }

    private PaymentQuery queryForApp(String outTradeNo, String appId) {
        OrderWechatPayProperties config = requireReady();
        Transaction transaction = queryTransaction(outTradeNo);
        if (transaction == null || transaction.getTradeState() == null
                || !outTradeNo.equals(transaction.getOutTradeNo())
                || !config.getMerchantId().equals(transaction.getMchid())
                || !appId.equals(transaction.getAppid())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信查单结果校验失败");
        }
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            return new PaymentQuery(transaction.getTradeState().name(), null);
        }
        if (transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                || transaction.getAmount().getTotal() <= 0
                || !"CNY".equals(transaction.getAmount().getCurrency())
                || !StringUtils.hasText(transaction.getTransactionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信查单金额或交易号无效");
        }
        return new PaymentQuery("SUCCESS", new VerifiedPayment(outTradeNo, transaction.getTransactionId(),
                BigDecimal.valueOf(transaction.getAmount().getTotal(), 2)));
    }

    /** SDK verifies the response signature before this transaction reaches the application. */
    protected Transaction queryTransaction(String outTradeNo) {
        var request = new com.wechat.pay.java.service.payments.h5.model.QueryOrderByOutTradeNoRequest();
        request.setMchid(requireReady().getMerchantId());
        request.setOutTradeNo(outTradeNo);
        return new H5Service.Builder().config(wechatConfig()).build().queryOrderByOutTradeNo(request);
    }

    @Override
    public Map<String, String> prepayH5(String outTradeNo, BigDecimal amount, String description, String clientIp) {
        OrderWechatPayProperties config = requireReady();
        if (!StringUtils.hasText(clientIp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少客户端IP");
        }
        requirePositiveAmount(amount);
        com.wechat.pay.java.service.payments.h5.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        request.setAppid(config.getAppId());
        request.setMchid(config.getMerchantId());
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
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
        if (!StringUtils.hasText(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信预支付失败");
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("type", "WECHAT_H5_URL");
        result.put("h5Url", url);
        result.put("outTradeNo", outTradeNo);
        return result;
    }

    @Override
    public Map<String, String> prepayJsapi(String outTradeNo, BigDecimal amount, String description, String openId) {
        return prepayJsapiForApp(outTradeNo, amount, description, openId,
                requireReady().getAppId(), requireReady().getNotifyUrl(), "WECHAT_JSAPI");
    }

    @Override
    public Map<String, String> prepayMiniProgram(String outTradeNo, BigDecimal amount, String description, String openId) {
        requireMiniReady();
        return prepayJsapiForApp(outTradeNo, amount, description, openId,
                miniProperties.getAppId(), miniProperties.getNotifyUrl(), "WECHAT_MINIPROGRAM");
    }

    private Map<String, String> prepayJsapiForApp(String outTradeNo, BigDecimal amount, String description,
                                                 String openId, String appId, String notifyUrl, String type) {
        OrderWechatPayProperties config = requireReady();
        if (!StringUtils.hasText(openId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 openId");
        }
        requirePositiveAmount(amount);
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        request.setAppid(appId);
        request.setMchid(config.getMerchantId());
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(notifyUrl);
        com.wechat.pay.java.service.payments.jsapi.model.Amount payAmount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        payAmount.setTotal(amount.movePointRight(2).intValueExact());
        payAmount.setCurrency("CNY");
        request.setAmount(payAmount);
        Payer payer = new Payer();
        payer.setOpenid(openId);
        request.setPayer(payer);
        PrepayWithRequestPaymentResponse response = sendJsapiPrepay(request);
        if (!appId.equals(response.getAppId())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信预支付应用校验失败");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("type", type);
        values.put("appId", response.getAppId());
        values.put("timeStamp", response.getTimeStamp());
        values.put("nonceStr", response.getNonceStr());
        values.put("package", response.getPackageVal());
        values.put("signType", response.getSignType());
        values.put("paySign", response.getPaySign());
        values.put("outTradeNo", outTradeNo);
        return values;
    }

    @Override
    public VerifiedPayment verifyNotify(String rawBody, String serial, String nonce,
                                        String signature, String timestamp) {
        return verifyForApp(rawBody, serial, nonce, signature, timestamp, requireReady().getAppId());
    }

    @Override
    public VerifiedPayment verifyMiniNotify(String rawBody, String serial, String nonce, String signature, String timestamp) {
        requireMiniReady();
        return verifyForApp(rawBody, serial, nonce, signature, timestamp, miniProperties.getAppId());
    }

    private VerifiedPayment verifyForApp(String rawBody, String serial, String nonce, String signature,
                                          String timestamp, String appId) {
        OrderWechatPayProperties config = requireReady();
        try {
            RequestParam param = new RequestParam.Builder()
                    .body(rawBody).serialNumber(serial).nonce(nonce)
                    .signature(signature).timestamp(timestamp).build();
            Transaction transaction = parseNotification(param);
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS
                    || !config.getMerchantId().equals(transaction.getMchid())
                    || !appId.equals(transaction.getAppid())
                    || transaction.getAmount() == null
                    || !"CNY".equals(transaction.getAmount().getCurrency())
                    || transaction.getAmount().getTotal() == null
                    || transaction.getAmount().getTotal() <= 0
                    || !StringUtils.hasText(transaction.getOutTradeNo())
                    || !StringUtils.hasText(transaction.getTransactionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付通知校验失败");
            }
            return new VerifiedPayment(transaction.getOutTradeNo(), transaction.getTransactionId(),
                    BigDecimal.valueOf(transaction.getAmount().getTotal(), 2));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("微信支付通知验签失败", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付通知校验失败");
        }
    }

    protected PrepayWithRequestPaymentResponse sendJsapiPrepay(
            com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request) {
        return new JsapiServiceExtension.Builder().config(wechatConfig()).build().prepayWithRequestPayment(request);
    }

    protected Transaction parseNotification(RequestParam param) {
        return new NotificationParser((NotificationConfig) wechatConfig()).parse(param, Transaction.class);
    }

    private void requireMiniReady() {
        requireReady();
        if (!miniProperties.isEnabled() || !StringUtils.hasText(miniProperties.getAppId())
                || !StringUtils.hasText(miniProperties.getNotifyUrl()) || !miniProperties.getNotifyUrl().startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "小程序微信支付尚未配置");
        }
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付金额无效");
        }
    }

    private OrderWechatPayProperties requireReady() {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getAppId())
                || !StringUtils.hasText(properties.getMerchantId())
                || !StringUtils.hasText(properties.getMerchantSerialNumber())
                || !StringUtils.hasText(properties.getApiV3Key())
                || !StringUtils.hasText(properties.getNotifyUrl())
                || !fileExists(properties.getPrivateKeyPath())
                || !WechatSdkConfigFactory.validMode(properties.getVerificationMode(),
                        properties.getPublicKeyId(), properties.getPublicKeyPath())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付渠道尚未启用");
        }
        return properties;
    }

    private Config wechatConfig() {
        Config current = wechatConfig;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (wechatConfig == null) {
                OrderWechatPayProperties p = requireReady();
                wechatConfig = WechatSdkConfigFactory.create(p.getVerificationMode(),
                        p.getMerchantId(), p.getPrivateKeyPath(), p.getMerchantSerialNumber(),
                        p.getApiV3Key(), p.getPublicKeyId(), p.getPublicKeyPath());
            }
            return wechatConfig;
        }
    }

    private boolean fileExists(String path) {
        return StringUtils.hasText(path) && Files.isRegularFile(Path.of(path));
    }
}
