package com.macro.mall.portal.config;

import com.wechat.pay.java.core.RSAPublicKeyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WechatSdkConfigFactoryTest {
    @TempDir Path tempDir;

    @Test void publicKeyModeBuildsWithoutDownloadingPlatformCertificates() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privateKey = tempDir.resolve("merchant.pem");
        Path publicKey = tempDir.resolve("wechatpay.pem");
        Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        var config = WechatSdkConfigFactory.create("PUBLIC_KEY", "merchant-test", privateKey.toString(),
                "merchant-serial-test", "0123456789abcdef0123456789abcdef",
                "PUB_KEY_ID_test", publicKey.toString());
        assertInstanceOf(RSAPublicKeyConfig.class, config);
        assertFalse(WechatSdkConfigFactory.validMode("PUBLIC_KEY", "PUB_KEY_ID_test", ""));
        assertFalse(WechatSdkConfigFactory.validMode("PUBLIC_KEY", "merchant-serial-test", publicKey.toString()));
        assertFalse(WechatSdkConfigFactory.validMode("UNKNOWN", "PUB_KEY_ID_test", publicKey.toString()));
        assertTrue(WechatSdkConfigFactory.validMode("CERTIFICATE", null, null));
    }

    private String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(value)
                + "\n-----END " + type + "-----\n";
    }
}
