package com.macro.mall.portal.controller;
import com.macro.mall.common.api.CommonResult;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={TriPoolMemberController.class,MembershipPaymentController.class})
public class TriPoolMemberErrors {
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public CommonResult<Void> invalidRequest(RuntimeException error) { return CommonResult.failed(error.getMessage()); }
}
