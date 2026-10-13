package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.service.impl.LocalMockMembershipPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 会员权益购买的本地支付桩入口：仅本机可调用，且仅在 dev 支付桩开启时注册。 */
@RestController
@RequestMapping("/mockPayment/membership")
@ConditionalOnProperty(name = "mall.payment.mock.enabled", havingValue = "true")
public class LocalMockMembershipPaymentController {

    private final LocalMockMembershipPaymentService service;

    public LocalMockMembershipPaymentController(LocalMockMembershipPaymentService service) {
        this.service = service;
    }

    @PostMapping("/confirm")
    public CommonResult<String> confirm(@RequestParam Long orderId, HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"::1".equals(remote) && !"0:0:0:0:0:0:0:1".equals(remote)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅供本地演示");
        }
        try {
            return CommonResult.success(service.confirm(orderId));
        } catch (MembershipPaymentException ex) {
            // 业务规则不满足（如已是同级/更高等级会员、订单金额不符）时给出可读原因
            return CommonResult.failed(String.valueOf(ex.getError()));
        }
    }
}
