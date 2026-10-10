package com.macro.mall.portal.config;

import com.macro.mall.common.api.CommonResult;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 依赖不可用时给前端明确反馈；原事务异常向上传播后会回滚订单和库存预占。 */
@RestControllerAdvice
public class PortalDependencyExceptionHandler {
    @ExceptionHandler(AmqpConnectException.class)
    public ResponseEntity<CommonResult> rabbitUnavailable(AmqpConnectException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CommonResult.failed("消息队列暂不可用，请稍后重试下单"));
    }
}
