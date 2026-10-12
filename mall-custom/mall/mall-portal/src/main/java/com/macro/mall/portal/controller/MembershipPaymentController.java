package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.domain.MembershipPurchaseRequest;
import com.macro.mall.portal.service.MembershipPaymentGateway;
import com.macro.mall.portal.service.MembershipPurchaseOrderService;
import com.macro.mall.portal.service.MembershipVerifiedPaymentHandler;
import com.macro.mall.portal.service.UmsMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** Real membership payment endpoints; the existing simulated purchase routes remain disabled. */
@RestController
@RequestMapping("/member/membership")
public class MembershipPaymentController {
    private static final Logger log = LoggerFactory.getLogger(MembershipPaymentController.class);
    private final UmsMemberService memberService;
    private final MembershipPurchaseOrderService orders;
    private final MembershipPaymentGateway gateway;
    private final MembershipVerifiedPaymentHandler verifiedPaymentHandler;

    public MembershipPaymentController(UmsMemberService memberService, MembershipPurchaseOrderService orders,
                                       MembershipPaymentGateway gateway,
                                       MembershipVerifiedPaymentHandler verifiedPaymentHandler) {
        this.memberService = memberService;
        this.orders = orders;
        this.gateway = gateway;
        this.verifiedPaymentHandler = verifiedPaymentHandler;
    }

    @PostMapping("/orders")
    public CommonResult<Map<String, Object>> create(@RequestBody MembershipPurchaseRequest request,
                                                     HttpServletRequest servletRequest) {
        try {
            Long memberId = memberService.getCurrentMember().getId();
            return CommonResult.success(orders.create(memberId, request, clientIp(servletRequest)));
        } catch (MembershipPaymentException ex) {
            return CommonResult.failed(ex.getError());
        }
    }

    @PostMapping("/notify/alipay")
    public ResponseEntity<String> alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        for (String key : request.getParameterMap().keySet()) params.put(key, request.getParameter(key));
        try {
            verifiedPaymentHandler.accept(gateway.verifyAlipay(params));
            return ResponseEntity.ok("success");
        } catch (MembershipPaymentException ex) {
            return callbackFailure(ex);
        } catch (Exception ex) {
            log.error("Membership Alipay callback processing failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failure");
        }
    }

    @PostMapping("/notify/wechat")
    public ResponseEntity<String> wechatNotify(@RequestBody String rawBody,
                                               @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
                                               @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                               @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                               @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp) {
        try {
            verifiedPaymentHandler.accept(gateway.verifyWechat(rawBody, serial, nonce, signature, timestamp));
            return ResponseEntity.ok("{}");
        } catch (MembershipPaymentException ex) {
            return callbackFailure(ex);
        } catch (Exception ex) {
            log.error("Membership WeChat callback processing failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"code\":\"FAIL\"}");
        }
    }

    private ResponseEntity<String> callbackFailure(MembershipPaymentException ex) {
        if (ex.getError() == MembershipPaymentError.INVALID_NOTIFICATION) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failure");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("failure");
    }

    private String clientIp(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        // Only a same-host reverse proxy may supply the original client IP.
        if ("127.0.0.1".equals(peer) || "::1".equals(peer)) {
            String forwarded = request.getHeader("X-Real-IP");
            if (forwarded != null && forwarded.matches("[0-9a-fA-F:.]{3,45}")) return forwarded;
        }
        return peer;
    }
}
