package com.macro.mall.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
public class TencentSmsProperties {
    private final Environment runtime;
    @Value("${MALL_SMS_ENABLED:false}")
    private boolean enabled;
    @Value("${TENCENT_SMS_SECRET_ID:}")
    private String secretId = "";
    @Value("${TENCENT_SMS_SECRET_KEY:}")
    private String secretKey = "";
    @Value("${TENCENT_SMS_SDK_APP_ID:}")
    private String sdkAppId = "";
    @Value("${TENCENT_SMS_SIGN_ID:}")
    private String signId = "";
    @Value("${TENCENT_SMS_SIGN_NAME:}")
    private String signName = "";
    @Value("${TENCENT_SMS_TEMPLATE_ID:}")
    private String templateId = "";
    @Value("${TENCENT_SMS_REGION:ap-guangzhou}")
    private String region = "ap-guangzhou";
    @Value("${TENCENT_SMS_SANDBOX_TEST_MODE:false}")
    private boolean sandboxTestMode;
    @Value("${MALL_SMS_ENVIRONMENT:production}")
    private String environment = "production";
    @Value("${MALL_SMS_TIMEOUT_MILLIS:5000}")
    private int timeoutMillis = 5000;

    public TencentSmsProperties(Environment runtime) {
        this.runtime = runtime;
    }

    public boolean sandboxSimulationAllowed() {
        String database = runtime.getProperty("spring.datasource.url", "");
        return sandboxTestMode && "sandbox".equals(environment)
                && Arrays.asList(runtime.getActiveProfiles()).contains("sandbox")
                && !Arrays.asList(runtime.getActiveProfiles()).contains("prod")
                && !Arrays.asList(runtime.getActiveProfiles()).contains("production")
                && database.matches("(?i).*[/]mall_demo_sandbox(?:[?;].*)?");
    }

    public boolean configured() {
        return enabled && !secretId.isBlank() && !secretKey.isBlank() && !sdkAppId.isBlank()
                && !signId.isBlank() && !signName.isBlank() && !templateId.isBlank() && !region.isBlank();
    }

    public Map<String, Object> publicStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", "TENCENT");
        status.put("enabled", enabled);
        status.put("configured", configured());
        status.put("sdkAppId", sdkAppId);
        status.put("signId", signId);
        status.put("signName", signName);
        status.put("templateId", templateId);
        status.put("region", region);
        status.put("secretIdConfigured", !secretId.isBlank());
        status.put("secretKeyConfigured", !secretKey.isBlank());
        status.put("sandboxTestMode", sandboxSimulationAllowed());
        status.put("codeLifetimeSeconds", 90);
        status.put("resendSeconds", 60);
        status.put("phoneDailyLimit", 10);
        status.put("ipMinuteLimit", 5);
        status.put("ipDailyLimit", 50);
        return status;
    }
}
