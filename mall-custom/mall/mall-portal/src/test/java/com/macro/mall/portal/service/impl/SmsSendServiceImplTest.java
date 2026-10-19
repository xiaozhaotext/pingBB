package com.macro.mall.portal.service.impl;

import com.macro.mall.portal.config.SmsProperties;
import com.macro.mall.portal.service.SmsSendService.SmsSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 短信通道：未启用/参数非法不得谎报已发送；凭证缺失与接口拒绝都要如实反馈。 */
class SmsSendServiceImplTest {

    /** 用固定响应替换真实短信请求，保证测试不出网。 */
    private static class StubService extends SmsSendServiceImpl {
        private String getResponse;
        private String postResponse;
        private String lastUrl;
        private Map<String, String> lastForm;

        @Override
        protected String httpGet(String url) {
            lastUrl = url;
            return getResponse;
        }

        @Override
        protected String httpPost(String url, Map<String, String> form) {
            lastUrl = url;
            lastForm = form;
            return postResponse;
        }
    }

    private final SmsProperties properties = new SmsProperties();
    private final StubService service = new StubService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    private void enableAliyun() {
        properties.setEnabled(true);
        properties.setProvider("ALIYUN");
        properties.setAccessKeyId("ak");
        properties.setAccessKeySecret("sk");
        properties.setSignName("智游美");
        properties.setTemplateCode("SMS_123456");
    }

    @Test
    void disabledChannelIsSkippedAndAllowsTestEcho() {
        SmsSendResult result = service.sendCode("13800000000", "123456");
        assertEquals("SKIPPED", result.status());
        assertFalse(result.sent());
        assertTrue(result.codeEchoAllowed());
    }

    @Test
    void invalidPhoneIsRejected() {
        properties.setEnabled(true);
        assertEquals("INVALID_PHONE", service.sendCode("abc", "123456").errorCode());
    }

    @Test
    void unknownProviderIsRejected() {
        properties.setEnabled(true);
        properties.setProvider("FOO");
        assertEquals("UNKNOWN_PROVIDER", service.sendCode("13800000000", "123456").errorCode());
    }

    @Test
    void aliyunWithoutCredentialsIsRejected() {
        properties.setEnabled(true);
        properties.setProvider("ALIYUN");
        assertEquals("SMS_CREDENTIALS_MISSING", service.sendCode("13800000000", "123456").errorCode());
    }

    @Test
    void aliyunSuccessDoesNotEchoCodeByDefault() {
        enableAliyun();
        service.getResponse = "{\"Code\":\"OK\",\"Message\":\"OK\",\"BizId\":\"1\"}";
        SmsSendResult result = service.sendCode("13800000000", "123456");
        assertTrue(result.sent());
        assertEquals("SENT", result.status());
        assertFalse(result.codeEchoAllowed());
        assertTrue(service.lastUrl.contains("Action=SendSms"));
        assertTrue(service.lastUrl.contains("Signature="));
        assertTrue(service.lastUrl.contains("13800000000"));
    }

    @Test
    void aliyunDevEchoFlagIsHonoured() {
        enableAliyun();
        properties.setDevReturnCode(true);
        service.getResponse = "{\"Code\":\"OK\"}";
        assertTrue(service.sendCode("13800000000", "123456").codeEchoAllowed());
    }

    @Test
    void aliyunRejectionIsSurfaced() {
        enableAliyun();
        service.getResponse = "{\"Code\":\"isv.SMS_SIGNATURE_ILLEGAL\",\"Message\":\"签名不合法\"}";
        SmsSendResult result = service.sendCode("13800000000", "123456");
        assertFalse(result.sent());
        assertEquals("isv.SMS_SIGNATURE_ILLEGAL", result.errorCode());
        assertEquals("签名不合法", result.errorMessage());
    }

    @Test
    void genericHttpSuccessRequiresKeyword() {
        properties.setEnabled(true);
        properties.setProvider("HTTP");
        properties.setGenericUrl("https://sms.example.com/send");
        properties.setGenericParams("mobile=${phone}&content=code:${code}");
        properties.setGenericSuccessKeyword("success");
        service.getResponse = "{\"status\":\"success\"}";
        SmsSendResult result = service.sendCode("13800000000", "123456");
        assertTrue(result.sent());
        assertTrue(service.lastUrl.contains("mobile=13800000000"));
        assertTrue(service.lastUrl.contains("content=code:123456"));
    }

    @Test
    void genericHttpMissingKeywordFails() {
        properties.setEnabled(true);
        properties.setProvider("HTTP");
        properties.setGenericUrl("https://sms.example.com/send");
        properties.setGenericParams("mobile=${phone}&content=${code}");
        properties.setGenericSuccessKeyword("success");
        service.getResponse = "{\"status\":\"error\"}";
        SmsSendResult result = service.sendCode("13800000000", "123456");
        assertFalse(result.sent());
        assertEquals("SEND_REJECTED", result.errorCode());
    }

    @Test
    void genericHttpPostSendsFormParams() {
        properties.setEnabled(true);
        properties.setProvider("HTTP");
        properties.setGenericMethod("POST");
        properties.setGenericUrl("https://sms.example.com/send");
        properties.setGenericParams("mobile=${phone}&text=${code}");
        service.postResponse = "ok";
        SmsSendResult result = service.sendCode("13800000000", "654321");
        assertTrue(result.sent());
        assertEquals("13800000000", service.lastForm.get("mobile"));
        assertEquals("654321", service.lastForm.get("text"));
        assertNull(result.errorCode());
    }
}