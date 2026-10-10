package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.impl.LocalMockPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 明确隔离于真实支付渠道，只允许本地演示进程调用。 */
@RestController
@RequestMapping("/mockPayment")
@ConditionalOnProperty(name = "mall.payment.mock.enabled", havingValue = "true")
public class LocalMockPaymentController {
    private final LocalMockPaymentService service;

    public LocalMockPaymentController(LocalMockPaymentService service) {
        this.service = service;
    }

    @PostMapping("/confirm")
    public CommonResult<String> confirm(@RequestParam Long orderId, HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"::1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅供本地演示");
        }
        return CommonResult.success(service.confirm(orderId));
    }
}
