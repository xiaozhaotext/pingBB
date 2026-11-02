package com.macro.mall.portal.config;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/** Selects the merchant's configured WeChat verification material without a silent fallback. */
public final class WechatSdkConfigFactory {
    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String PUBLIC_KEY = "PUBLIC_KEY";

    private WechatSdkConfigFactory() {}

    public static boolean validMode(String mode, String publicKeyId, String publicKeyPath) {
        if (CERTIFICATE.equals(mode)) return true;
        return PUBLIC_KEY.equals(mode) && StringUtils.hasText(publicKeyId)
                && publicKeyId.startsWith("PUB_KEY_ID_") && StringUtils.hasText(publicKeyPath)
                && Files.isRegularFile(Path.of(publicKeyPath)) && Files.isReadable(Path.of(publicKeyPath));
    }

    public static Config create(String mode, String merchantId, String privateKeyPath,
                                String merchantSerialNumber, String apiV3Key,
                                String publicKeyId, String publicKeyPath) {
        if (!validMode(mode, publicKeyId, publicKeyPath)) {
            throw new IllegalArgumentException("微信支付验签模式或公钥配置无效");
        }
        if (PUBLIC_KEY.equals(mode)) {
            return new RSAPublicKeyConfig.Builder()
                    .merchantId(merchantId).privateKeyFromPath(privateKeyPath)
                    .merchantSerialNumber(merchantSerialNumber).apiV3Key(apiV3Key)
                    .publicKeyId(publicKeyId).publicKeyFromPath(publicKeyPath).build();
        }
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId).privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(merchantSerialNumber).apiV3Key(apiV3Key).build();
    }
}
