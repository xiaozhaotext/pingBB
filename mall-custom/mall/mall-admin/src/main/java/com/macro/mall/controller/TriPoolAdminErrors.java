package com.macro.mall.controller;
import com.macro.mall.common.api.CommonResult;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=TriPoolAdminController.class)
public class TriPoolAdminErrors {
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public CommonResult<Void> invalidRequest(RuntimeException error) { return CommonResult.failed(error.getMessage()); }
}
