package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.WechatOrderPayService;
import com.macro.mall.portal.service.WechatWebOauthService;
import com.macro.mall.portal.service.impl.OrderWechatPayHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 普通订单微信支付：H5 / JSAPI 下单与支付结果通知。
 * 渠道未启用时统一返回 503；通知必须验签、核对金额并幂等入账。
 */
@Slf4j
@RestController
@Tag(name = "WechatOrderPayController", description = "普通订单微信支付")
@RequestMapping("/wechatPay")
public class WechatOrderPayController {

    @Autowired
    private WechatOrderPayService wechatOrderPayService;
    @Autowired
    private OrderWechatPayHandler orderWechatPayHandler;
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private WechatWebOauthService wechatOauth;
    @Autowired
    private JdbcTemplate jdbc;

    @Value("${mall.payment.wechat.enabled:false}")
    private boolean paymentEnabled;

    @Operation(summary = "微信H5下单")
    @RequestMapping(value = "/h5", method = RequestMethod.POST)
    public CommonResult<Map<String, String>> h5(@RequestParam String orderSn, HttpServletRequest request) {
        OmsOrder order = requireOwnPendingOrder(orderSn);
        String clientIp = resolveClientIp(request);
        return CommonResult.success(wechatOrderPayService.prepayH5(
                order.getOrderSn(), order.getPayAmount(), "订单支付 " + order.getOrderSn(), clientIp));
    }

    @Operation(summary = "微信JSAPI下单")
    @RequestMapping(value = "/jsapi", method = RequestMethod.POST)
    public CommonResult<Map<String, String>> jsapi(@RequestParam String orderSn) {
        OmsOrder order = requireOwnPendingOrder(orderSn);
        String openId = wechatOauth.openId(memberService.getCurrentMember().getId(), "ORDER");
        return CommonResult.success(wechatOrderPayService.prepayJsapi(
                order.getOrderSn(), order.getPayAmount(), "订单支付 " + order.getOrderSn(), openId));
    }

    @Operation(summary = "微信支付结果通知")
    @RequestMapping(value = "/notify", method = RequestMethod.POST)
    public String notify(@RequestBody String rawBody,
                         @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
                         @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                         @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                         @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp) {
        if (!paymentEnabled) {
            return "failure";
        }
        WechatOrderPayService.VerifiedPayment payment =
                wechatOrderPayService.verifyNotify(rawBody, serial, nonce, signature, timestamp);
        return orderWechatPayHandler.handle(payment);
    }

    private OmsOrder requireOwnPendingOrder(String orderSn) {
        if (!paymentEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付渠道尚未启用");
        }
        OmsOrder order = portalOrderService.getOrderByOrderSn(orderSn);
        if (order == null || order.getPayAmount() == null
                || !memberService.getCurrentMember().getId().equals(order.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "订单不存在或无权支付");
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不可支付");
        }
        Integer route = jdbc.queryForObject("SELECT COUNT(*) FROM tri_order_snapshot WHERE order_id=?",
                Integer.class, order.getId());
        if (route == null || route != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前仅开放旅游路线真实支付");
        }
        return order;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
