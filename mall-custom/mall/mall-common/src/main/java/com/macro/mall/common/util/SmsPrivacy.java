package com.macro.mall.common.util;

public final class SmsPrivacy {
    private SmsPrivacy() {}

    public static String maskPhone(String phone) {
        return phone != null && phone.matches("1[3-9][0-9]{9}")
                ? phone.substring(0, 3) + "****" + phone.substring(7) : "***";
    }

    public static String safeMessage(String message, String phone, String code) {
        String result = message == null ? "" : message;
        if (phone != null && !phone.isEmpty()) result = result.replace(phone, maskPhone(phone));
        if (code != null && !code.isEmpty()) result = result.replace(code, "******");
        result = result.replaceAll("(?<![0-9])1[3-9][0-9]{9}(?![0-9])", "***")
                .replaceAll("AKID[A-Za-z0-9]+", "***");
        return result.substring(0, Math.min(result.length(), 500));
    }
}
