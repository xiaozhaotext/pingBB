package com.macro.mall.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.config.TencentSmsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TencentSmsSenderTest {
    private final ObjectMapper json = new ObjectMapper();

    private TencentSmsProperties config(MockEnvironment environment) {
        TencentSmsProperties config = new TencentSmsProperties(environment);
        config.setEnabled(true);
        config.setSecretId("test-secret-id");
        config.setSecretKey("test-secret-key");
        config.setSdkAppId("test-sdk-app-id");
        config.setSignId("test-sign-id");
        config.setSignName("测试签名");
        config.setTemplateId("test-template-id");
        return config;
    }

    @Test void sendsOrderedTemplateParametersAndRecordsProviderReceipt() throws Exception {
        TencentSmsProperties config = config(new MockEnvironment());
        TencentSmsSender sender = new TencentSmsSender(config, json, 90) {
            @Override protected String post(String body, Map<String,String> headers) {
                try {
                    var payload = json.readTree(body);
                    assertEquals("123456", payload.at("/TemplateParamSet/0").asText());
                    assertEquals("1", payload.at("/TemplateParamSet/1").asText());
                    for (var parameter : payload.path("TemplateParamSet")) {
                        assertTrue(parameter.asText().matches("[0-9]{1,6}"),
                                "Tencent verification template parameters must contain digits only");
                    }
                    assertEquals("+8613800000000", payload.at("/PhoneNumberSet/0").asText());
                    assertEquals("2021-01-11", headers.get("X-TC-Version"));
                    assertEquals("SendSms", headers.get("X-TC-Action"));
                    assertTrue(headers.get("Authorization").contains("TC3-HMAC-SHA256 Credential="));
                    return "{\"Response\":{\"RequestId\":\"request-test\",\"SendStatusSet\":[{\"Code\":\"Ok\",\"Message\":\"send success\",\"SerialNo\":\"serial-test\"}]}}";
                } catch (Exception error) { throw new AssertionError(error); }
            }
        };
        var result = sender.send("13800000000", "123456");
        assertTrue(result.sent());
        assertFalse(result.codeEchoAllowed());
        assertEquals("Ok", result.errorCode());
        assertEquals("request-test", result.requestId());
        assertEquals("serial-test", result.serialNo());
    }

    @Test void providerRejectionsAreMasked() {
        TencentSmsSender sender = new TencentSmsSender(config(new MockEnvironment()), json, 90) {
            @Override protected String post(String body, Map<String,String> headers) {
                return "{\"Response\":{\"RequestId\":\"request-test\",\"SendStatusSet\":[{\"Code\":\"FailedOperation.TemplateIncorrectOrUnapproved\",\"Message\":\"phone 13800000000 code 123456 rejected\"}]}}";
            }
        };
        var result = sender.send("13800000000", "123456");
        assertFalse(result.sent());
        assertFalse(result.errorMessage().contains("13800000000"));
        assertFalse(result.errorMessage().contains("123456"));
        assertEquals("FailedOperation.TemplateIncorrectOrUnapproved", result.errorCode());
    }

    @Test void productionCannotEnableSandboxByChangingOnlyTheTestFlag() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/mall_demo");
        env.setActiveProfiles("dev");
        var config = config(env);
        config.setSandboxTestMode(true);
        config.setEnvironment("sandbox");
        assertFalse(config.sandboxSimulationAllowed());
        config.setSecretKey("");
        var result = new TencentSmsSender(config, json, 90).send("13800000000", "123456");
        assertFalse(result.sent());
        assertEquals("CONFIGURATION_MISSING", result.errorCode());
    }

    @Test void sandboxSimulationIsExplicitAndNeverEchoesCode() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/mall_demo_sandbox");
        env.setActiveProfiles("sandbox");
        var config = config(env);
        assertFalse(config.sandboxSimulationAllowed());
        config.setSandboxTestMode(true);
        config.setEnvironment("sandbox");
        var result = new TencentSmsSender(config, json, 90).send("13800000000", "123456");
        assertFalse(result.sent());
        assertFalse(result.codeEchoAllowed());
        assertEquals("SKIPPED", result.status());
        env.setActiveProfiles("sandbox", "prod");
        assertFalse(config.sandboxSimulationAllowed());
    }
}
