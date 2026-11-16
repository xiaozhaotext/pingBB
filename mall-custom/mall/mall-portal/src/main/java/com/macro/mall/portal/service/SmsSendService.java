package com.macro.mall.portal.service;

/**
 * 短信发送能力。返回值用于如实记录发送结果并决定是否回显验证码。
 */
public interface SmsSendService {

    SmsSendResult sendCode(String telephone, String code);

    record SmsSendResult(boolean sent, boolean codeEchoAllowed, String status, String provider,
                         String errorCode, String errorMessage, String requestId, String serialNo) {

        public SmsSendResult(boolean sent, boolean codeEchoAllowed, String status, String provider,
                             String errorCode, String errorMessage) {
            this(sent, false, status, provider, errorCode, errorMessage, null, null);
        }

        public static SmsSendResult sent(boolean codeEchoAllowed, String provider) {
            return new SmsSendResult(true, false, "SENT", provider, "Ok", "accepted");
        }

        /** 通道未启用：未发送，但允许（仅测试环境）通过接口返回验证码。 */
        public static SmsSendResult skipped(String provider, String reason) {
            return new SmsSendResult(false, false, "SKIPPED", provider, "SANDBOX_TEST", reason);
        }

        public static SmsSendResult failed(String provider, String errorCode, String errorMessage) {
            return new SmsSendResult(false, false, "FAILED", provider, errorCode, errorMessage);
        }
    }
}
