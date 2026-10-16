package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.service.RechargeService;
import com.macro.mall.portal.service.UmsMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/member/recharge")
public class RechargeController {
    private static final Logger log=LoggerFactory.getLogger(RechargeController.class);
    private final RechargeService service;
    private final UmsMemberService members;
    public RechargeController(RechargeService service,UmsMemberService members) { this.service=service; this.members=members; }
    public record CreateRequest(String amount,String channel,String requestId) {}
    private long member() { return members.getCurrentMember().getId(); }
    private <T> CommonResult<T> result(Supplier<T> action) {
        try { return CommonResult.success(action.get()); }
        catch (MembershipPaymentException ex) { return CommonResult.failed(ex.getError()); }
        catch (IllegalArgumentException ex) { return CommonResult.failed(ex.getMessage()); }
    }
    @GetMapping("/account")
    public CommonResult<?> account() { return result(()->service.account(member())); }
    @GetMapping("/ledger")
    public CommonResult<?> ledger() { return result(()->service.ledger(member())); }
    @GetMapping("/payment-options")
    public CommonResult<?> paymentOptions() { member(); return result(service::paymentOptions); }
    @PostMapping("/orders/{no}/sandbox-confirm")
    public CommonResult<?> confirmSandbox(@PathVariable String no) { return result(()->service.confirmSandbox(member(),no)); }
    @GetMapping("/orders")
    public CommonResult<?> history() { return result(()->service.history(member())); }
    @GetMapping("/orders/{no}")
    public CommonResult<?> order(@PathVariable String no) { return result(()->service.order(member(),no)); }
    @PostMapping("/orders")
    public CommonResult<?> create(@RequestBody CreateRequest request) {
        return result(()->service.create(member(),request.amount(),request.channel(),request.requestId()));
    }
    @PostMapping("/orders/{no}/pay")
    public CommonResult<?> pay(@PathVariable String no,HttpServletRequest request) {
        String peer=request.getRemoteAddr();
        String forwarded=request.getHeader("X-Real-IP");
        String ip=("127.0.0.1".equals(peer)||"::1".equals(peer)) && forwarded!=null && forwarded.matches("[0-9a-fA-F:.]{3,45}")?forwarded:peer;
        return result(()->service.pay(member(),no,ip));
    }
    @PostMapping("/notify/alipay")
    public ResponseEntity<String> alipay(HttpServletRequest request) {
        Map<String,String> params=new HashMap<>();
        for (String key:request.getParameterMap().keySet()) params.put(key,request.getParameter(key));
        try { service.accept(service.gateway().verifyAlipay(params)); return ResponseEntity.ok("success"); }
        catch (Exception ex) {
            log.warn("Recharge Alipay notification rejected or settlement failed ({})",ex.getClass().getSimpleName());
            return ResponseEntity.status(500).body("failure");
        }
    }
    @PostMapping("/notify/wechat")
    public ResponseEntity<String> wechat(@RequestBody String body,
            @RequestHeader("Wechatpay-Serial") String serial,@RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,@RequestHeader("Wechatpay-Timestamp") String timestamp) {
        try { service.accept(service.gateway().verifyWechat(body,serial,nonce,signature,timestamp)); return ResponseEntity.ok("{}"); }
        catch (Exception ex) {
            log.warn("Recharge WeChat notification rejected or settlement failed ({})",ex.getClass().getSimpleName());
            return ResponseEntity.status(500).body("{\"code\":\"FAIL\"}");
        }
    }
}
