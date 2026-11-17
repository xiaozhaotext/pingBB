package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.WechatOrderPayService;
import com.macro.mall.portal.service.WechatWebOauthService;
import com.macro.mall.portal.service.WechatMiniProgramService;
import com.macro.mall.portal.service.WechatMiniPaymentService;
import org.springframework.http.ResponseEntity;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.math.BigDecimal;

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
    @Autowired
    private WechatMiniProgramService miniProgram;
    @Autowired
    private WechatMiniPaymentService miniPayments;

    @Value("${mall.payment.wechat.enabled:false}")
    private boolean paymentEnabled;
    @Value("${mall.payment.wechat.sandbox-one-yuan-test-enabled:false}")
    private boolean sandboxOneYuanTestEnabled;
    @Value("${mall.payment.wechat.production-test-order-sn:}")
    private String productionTestOrderSn;
    @Value("${mall.payment.wechat.production-test-product-id:0}")
    private long productionTestProductId;
    @Value("${mall.payment.wechat.all-products-enabled:false}")
    private boolean allProductsEnabled;

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

    public record MiniPrepayRequest(@NotBlank @Size(max = 64) String orderSn,
                                    @NotBlank @Size(min = 32, max = 32) String paymentTicket) {}

    @PostMapping("/mini")
    public CommonResult<Map<String, String>> mini(@Valid @RequestBody MiniPrepayRequest request) {
        OmsOrder order = requireOwnPendingOrder(request.orderSn());
        String openId = miniProgram.paymentOpenId(memberService.getCurrentMember().getId(), request.paymentTicket());
        return CommonResult.success(miniPayments.prepay(order, openId));
    }

    @PostMapping("/mini/notify")
    public ResponseEntity<Void> miniNotify(@RequestBody String rawBody,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp) {
        var verified = wechatOrderPayService.verifyMiniNotify(rawBody, serial, nonce, signature, timestamp);
        String result = orderWechatPayHandler.handle(miniPayments.originalPayment(verified));
        if (!"success".equals(result)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "支付通知尚未处理完成，请重试");
        }
        return ResponseEntity.noContent().build();
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

    @Operation(summary = "查询并确认本人订单的微信支付结果")
    @PostMapping("/status")
    public CommonResult<Map<String, Object>> status(@RequestParam String orderSn) {
        return paymentResult(orderSn, false);
    }

    @PostMapping("/mini/status")
    public CommonResult<Map<String, Object>> miniStatus(@RequestParam String orderSn) {
        return paymentResult(orderSn, true);
    }

    private CommonResult<Map<String, Object>> paymentResult(String orderSn, boolean mini) {
        OmsOrder order = requireOwnOrder(orderSn);
        if (isPaid(order)) return paymentStatus(order, "CONFIRMED", false);
        if (!paymentEnabled) return paymentStatus(order, "PENDING", false);
        var query = mini ? miniPayments.query(order) : wechatOrderPayService.queryPayment(orderSn);
        if (query.payment() != null) {
            // Same transactional, amount-checked, idempotent handler used by signed notifications.
            String result = orderWechatPayHandler.handle(query.payment());
            OmsOrder refreshed = requireOwnOrder(orderSn);
            return paymentStatus(refreshed, "success".equals(result) && isPaid(refreshed)
                    ? "CONFIRMED" : "REVIEW", false);
        }
        boolean closed = order.getStatus() != null && order.getStatus() != 0
                || "CLOSED".equals(query.tradeState()) || "REVOKED".equals(query.tradeState());
        return paymentStatus(order, closed ? "CLOSED" : "PENDING",
                !closed && "NOTPAY".equals(query.tradeState()));
    }

    private CommonResult<Map<String, Object>> paymentStatus(OmsOrder order, String state, boolean canRetry) {
        return CommonResult.success(Map.of("orderId", order.getId(), "orderSn", order.getOrderSn(),
                "state", state, "canRetry", canRetry));
    }

    private boolean isPaid(OmsOrder order) {
        return order.getStatus() != null && order.getStatus() >= 1 && order.getStatus() <= 3;
    }

    private OmsOrder requireOwnOrder(String orderSn) {
        OmsOrder order = portalOrderService.getOrderByOrderSn(orderSn);
        if (order == null || order.getPayAmount() == null
                || !memberService.getCurrentMember().getId().equals(order.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "订单不存在或无权访问");
        }
        return order;
    }

    private OmsOrder requireOwnPendingOrder(String orderSn) {
        if (!paymentEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付渠道尚未启用");
        }
        OmsOrder order = requireOwnOrder(orderSn);
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不可支付");
        }
        // Zone orders may have a genuine external balance after points and wallet deductions.
        // Keep the existing test-order restrictions for PICK membership and unassigned goods.
        Integer zoneItems = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order_item WHERE order_id=? AND zone_code IN ('REBUY','CONSUME')",
                Integer.class, order.getId());
        Integer allZoneItems = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order_item WHERE order_id=?",
                Integer.class, order.getId());
        if (zoneItems != null && zoneItems > 0 && zoneItems.equals(allZoneItems)
                && isPayableProductOrder(order, allZoneItems)) return order;
        if (productionTestOrderSn != null && !productionTestOrderSn.isBlank()) {
            String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
            if (!isProductionTestOrder(order, database, productionTestOrderSn)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前仅开放指定订单测试");
            }
            return order;
        }
        if (productionTestProductId > 0) {
            String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
            Integer matchingItems = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oms_order_item WHERE order_id=? AND product_id=? AND product_quantity=1",
                    Integer.class, order.getId(), productionTestProductId);
            Integer allItems = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order_item WHERE order_id=?",
                    Integer.class, order.getId());
            if (isProductionHalfYuanProductOrder(order, database, matchingItems, allItems)) return order;
            // The test product must never ride along with a normal order and trigger benefits.
            Integer testItems = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM oms_order_item WHERE order_id=? AND product_id=?",
                    Integer.class, order.getId(), productionTestProductId);
            if (testItems != null && testItems > 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Test product requires its own exact order");
            }
        }
        if (allProductsEnabled) {
            Integer itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM oms_order_item WHERE order_id=?",
                    Integer.class, order.getId());
            if (isPayableProductOrder(order, itemCount)) return order;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order amount or items invalid");
        }
        Integer route = jdbc.queryForObject("SELECT COUNT(*) FROM tri_order_snapshot WHERE order_id=?",
                Integer.class, order.getId());
        if (route == null || route != 1) {
            String database = sandboxOneYuanTestEnabled
                    ? jdbc.queryForObject("SELECT DATABASE()", String.class) : null;
            if (isOneYuanSandboxOrder(order, database, sandboxOneYuanTestEnabled)) return order;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前仅开放旅游路线真实支付");
        }
        return order;
    }

    static boolean isPayableProductOrder(OmsOrder order, Integer itemCount) {
        return order.getPayAmount() != null && order.getPayAmount().signum() > 0
                && order.getTotalAmount() != null && order.getTotalAmount().signum() > 0
                && itemCount != null && itemCount > 0;
    }

    static boolean isOneYuanSandboxOrder(OmsOrder order, String database, boolean enabled) {
        return enabled && "mall_demo_sandbox".equals(database)
                && order.getTotalAmount() != null && order.getTotalAmount().compareTo(BigDecimal.ONE) == 0
                && order.getPayAmount() != null && order.getPayAmount().compareTo(BigDecimal.ONE) == 0
                && (order.getBalanceAmount() == null || order.getBalanceAmount().signum() == 0);
    }

    static boolean isProductionTestOrder(OmsOrder order, String database, String configuredOrderSn) {
        return "mall_demo".equals(database) && configuredOrderSn != null
                && configuredOrderSn.equals(order.getOrderSn())
                && (order.getBalanceAmount() == null || order.getBalanceAmount().signum() == 0);
    }

    static boolean isProductionHalfYuanProductOrder(OmsOrder order, String database,
                                                    Integer matchingItems, Integer allItems) {
        return "mall_demo".equals(database)
                && order.getTotalAmount() != null && order.getTotalAmount().compareTo(new BigDecimal("0.50")) == 0
                && order.getPayAmount() != null && order.getPayAmount().compareTo(new BigDecimal("0.50")) == 0
                && (order.getBalanceAmount() == null || order.getBalanceAmount().signum() == 0)
                && Integer.valueOf(1).equals(matchingItems) && Integer.valueOf(1).equals(allItems);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
