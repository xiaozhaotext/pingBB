package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.MembershipPaymentException;
import com.macro.mall.portal.service.RechargeService;
import com.macro.mall.portal.service.WalletWithdrawalService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.WechatMiniProgramService;
import com.macro.mall.portal.service.WechatOrderPayService;
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
    private final WalletWithdrawalService withdrawals;
    private final WechatMiniProgramService miniProgram;
    private final WechatOrderPayService wechatPay;
    public RechargeController(RechargeService service,UmsMemberService members,WalletWithdrawalService withdrawals,
                              WechatMiniProgramService miniProgram,WechatOrderPayService wechatPay) {
        this.service=service; this.members=members; this.withdrawals=withdrawals;
        this.miniProgram=miniProgram; this.wechatPay=wechatPay;
    }
    public record CreateRequest(String amount,String channel,String requestId) {}
    private long member() { return members.getCurrentMember().getId(); }
    private <T> CommonResult<T> result(Supplier<T> action) {
        try { return CommonResult.success(action.get()); }
        catch (MembershipPaymentException ex) { return CommonResult.failed(ex.getError()); }
        catch (IllegalArgumentException ex) { return CommonResult.failed(ex.getMessage()); }
    }
    @GetMapping("/account")
    public CommonResult<?> account() { return result(()->service.account(member())); }
    @GetMapping("/withdrawals")
    public CommonResult<?> withdrawals() { return result(()->withdrawals.list(member())); }
    @PostMapping("/withdrawals")
    public CommonResult<?> requestWithdrawal(@RequestBody WalletWithdrawalService.Request request) {
        return result(()->withdrawals.apply(member(),request));
    }
    @GetMapping("/ledger")
    public CommonResult<?> ledger() { return result(()->service.ledger(member())); }
    @GetMapping("/payment-options")
    public CommonResult<?> paymentOptions() { member(); return result(service::paymentOptions); }
    @GetMapping("/mini-payment-options")
    public CommonResult<?> miniPaymentOptions() { member(); return result(()->service.miniPaymentOptions(wechatPay)); }
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
    @PostMapping("/mini-orders")
    public CommonResult<?> createMini(@RequestBody CreateRequest request) {
        return result(()->service.createMini(member(),request.amount(),request.channel(),request.requestId(),wechatPay));
    }
    @PostMapping("/orders/{no}/pay")
    public CommonResult<?> pay(@PathVariable String no,@RequestParam(required = false) String mode,HttpServletRequest request) {
        String peer=request.getRemoteAddr();
        String forwarded=request.getHeader("X-Real-IP");
        String ip=("127.0.0.1".equals(peer)||"::1".equals(peer)) && forwarded!=null && forwarded.matches("[0-9a-fA-F:.]{3,45}")?forwarded:peer;
        return result(()->service.pay(member(),no,ip,mode));
    }
    public record MiniPayRequest(String paymentTicket) {}
    @PostMapping("/orders/{no}/mini-pay")
    public CommonResult<?> miniPay(@PathVariable String no,@RequestBody MiniPayRequest request) {
        return result(()->{
            long member=member();
            if (request==null || request.paymentTicket()==null || request.paymentTicket().isBlank())
                throw new IllegalArgumentException("微信支付授权已失效，请重试");
            String openId=miniProgram.paymentOpenId(member,request.paymentTicket());
            return service.payMini(member,no,openId,wechatPay);
        });
    }
    @PostMapping("/orders/{no}/mini-status")
    public CommonResult<?> miniStatus(@PathVariable String no) {
        return result(()->{
            long member=member();
            Map<String,Object> order=service.order(member,no);
            if (!"WECHAT".equals(order.get("channel"))) throw new IllegalArgumentException("该订单不是微信充值订单");
            if (!"PAID".equals(order.get("status"))) {
                var query=wechatPay.queryMiniPayment(no);
                if (query.payment()!=null)
                    service.accept(new com.macro.mall.portal.service.MembershipPaymentGateway.VerifiedPayment(
                            query.payment().outTradeNo(),query.payment().transactionId(),"WECHAT",query.payment().amount()));
            }
            return service.order(member,no);
        });
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
