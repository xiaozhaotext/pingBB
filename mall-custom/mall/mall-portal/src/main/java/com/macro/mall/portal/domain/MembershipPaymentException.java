package com.macro.mall.portal.domain;

public class MembershipPaymentException extends RuntimeException {
    private final MembershipPaymentError error;

    public MembershipPaymentException(MembershipPaymentError error) {
        super(error.getMessage());
        this.error = error;
    }

    public MembershipPaymentError getError() { return error; }
}
