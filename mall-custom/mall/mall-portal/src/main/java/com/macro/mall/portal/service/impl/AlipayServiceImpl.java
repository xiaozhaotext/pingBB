package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.config.AlipayConfig;
import com.macro.mall.portal.domain.AliPayParam;
import com.macro.mall.portal.service.AlipayService;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.model.OmsOrderExample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @auther macrozheng
 * @description 支付宝支付Service实现类
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {
    @Autowired
    private AlipayConfig alipayConfig;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PaymentExceptionRecorder paymentExceptionRecorder;

    private OmsOrder requireOwnOrder(String orderSn) {
        OmsOrder order = portalOrderService.getOrderByOrderSn(orderSn);
        if (order == null || order.getPayAmount() == null
                || !memberService.getCurrentMember().getId().equals(order.getMemberId())) {
            Asserts.fail("订单不存在或无权支付");
        }
        return order;
    }
    private OmsOrder requireOwnPendingOrder(String orderSn) {
        OmsOrder order = requireOwnOrder(orderSn);
        if (order.getStatus() == null || order.getStatus() != 0) {
            Asserts.fail("订单当前状态不可支付");
        }
        Long route = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tri_order_snapshot WHERE order_id=?",
                Long.class, order.getId());
        if (route == null || route != 1) {
            Asserts.fail("当前仅开放旅游路线真实支付");
        }
        return order;
    }
    @Override
    public String pay(AliPayParam aliPayParam) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //异步接收地址，公网可访问
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        if(StrUtil.isNotEmpty(alipayConfig.getReturnUrl())){
            //同步跳转地址
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        //商户订单号，商家自定义，保持唯一性
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //订单总金额以数据库中订单表中为准
        OmsOrder order = requireOwnPendingOrder(aliPayParam.getOutTradeNo());
        BigDecimal totalAmount = order.getPayAmount();
        //支付金额，最小值0.01元
        bizContent.put("total_amount", totalAmount);
        //订单标题，不可使用特殊符号
        bizContent.put("subject", aliPayParam.getSubject());
        //电脑网站支付场景固定传值FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());
        String formHtml = null;
        try {
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return formHtml;
    }

    @Override
    @Transactional
    public String notify(Map<String, String> params) {
        boolean signVerified = false;
        try {
            //调用SDK验证签名
            signVerified = AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(), alipayConfig.getCharset(), alipayConfig.getSignType());
        } catch (AlipayApiException e) {
            log.error("支付回调签名校验异常！",e);
        }
        if (!signVerified) {
            log.warn("支付回调签名校验失败！");
            return "failure";
        }
        String orderSn = params.get("out_trade_no");
        String amountText = params.get("total_amount");
        if (StrUtil.isBlank(orderSn) || StrUtil.isBlank(params.get("trade_no"))
                || StrUtil.isBlank(alipayConfig.getAppId()) || StrUtil.isBlank(alipayConfig.getSellerId())
                || !alipayConfig.getAppId().equals(params.get("app_id"))
                || !alipayConfig.getSellerId().equals(params.get("seller_id"))
                || (StrUtil.isNotBlank(params.get("currency")) && !"CNY".equals(params.get("currency")))) {
            log.warn("支付回调商户或交易标识不匹配");
            return "failure";
        }
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andOrderSnEqualTo(orderSn).andDeleteStatusEqualTo(0);
        java.util.List<OmsOrder> orders = orderMapper.selectByExample(example);
        if (orders.size() != 1 || orders.get(0).getPayAmount() == null || StrUtil.isBlank(amountText)) {
            return "failure";
        }
        OmsOrder order = orders.get(0);
        try {
            if (new BigDecimal(amountText).compareTo(order.getPayAmount()) != 0) {
                log.warn("支付回调金额与订单不匹配，orderSn:{}", orderSn);
                return "failure";
            }
        } catch (NumberFormatException ex) {
            return "failure";
        }
        if (!"TRADE_SUCCESS".equals(params.get("trade_status"))) {
            return "failure";
        }
        String tradeNo = params.get("trade_no");
        if (order.getStatus() != null && order.getStatus() >= 1 && order.getStatus() <= 3) {
            Long matching = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oms_payment_confirmation WHERE order_id=? AND channel_code='ALIPAY' AND channel_trade_no=?",
                    Long.class, order.getId(), tradeNo);
            if (matching != null && matching == 1) return "success";
            paymentExceptionRecorder.record(order, tradeNo, order.getPayAmount(), "SECOND_OR_UNMATCHED_PAYMENT");
            return "success";
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            paymentExceptionRecorder.record(order, tradeNo, order.getPayAmount(), "LATE_PAYMENT_CLOSED_ORDER");
            log.error("迟到支付已进入人工退款补偿队列，orderSn:{},status:{}", orderSn, order.getStatus());
            return "success";
        }
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO oms_payment_confirmation"
                + "(order_id,channel_code,channel_trade_no,paid_amount,currency_code) VALUES(?,'ALIPAY',?,?,'CNY')",
                order.getId(), tradeNo, order.getPayAmount());
        if (inserted != 1) {
            Long matching = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oms_payment_confirmation WHERE order_id=? AND channel_code='ALIPAY' AND channel_trade_no=?",
                    Long.class, order.getId(), tradeNo);
            if (matching != null && matching == 1) return "success";
            paymentExceptionRecorder.record(order, tradeNo, order.getPayAmount(), "DUPLICATE_TRADE_OR_ORDER");
            return "success";
        }
        portalOrderService.paySuccessByOrderSn(orderSn, 1);
        return "success";
    }

    @Override
    public String query(String outTradeNo, String tradeNo) {
        requireOwnOrder(outTradeNo);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        // 仅查询已完成当前会员归属校验的订单号，忽略客户端传入的渠道交易号。
        bizContent.put("out_trade_no", outTradeNo);
        //交易结算信息: trade_settle_info
        String[] queryOptions = {"trade_settle_info"};
        bizContent.put("query_options", queryOptions);
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            log.error("查询支付宝账单异常！",e);
        }
        if(response != null && response.isSuccess()){
            log.info("查询支付宝账单成功！");
        } else {
            log.error("查询支付宝账单失败！");
            return "QUERY_FAILED";
        }
        //交易状态：WAIT_BUYER_PAY（交易创建，等待买家付款）、TRADE_CLOSED（未付款交易超时关闭，或支付完成后全额退款）、TRADE_SUCCESS（交易支付成功）、TRADE_FINISHED（交易结束，不可退款）
        return response.getTradeStatus();
    }

    @Override
    public String webPay(AliPayParam aliPayParam) {
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest ();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //异步接收地址，公网可访问
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        if(StrUtil.isNotEmpty(alipayConfig.getReturnUrl())){
            //同步跳转地址
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        //商户订单号，商家自定义，保持唯一性
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //订单总金额以数据库中订单表中为准
        OmsOrder order = requireOwnPendingOrder(aliPayParam.getOutTradeNo());
        BigDecimal totalAmount = order.getPayAmount();
        //支付金额，最小值0.01元
        bizContent.put("total_amount", totalAmount);
        //订单标题，不可使用特殊符号
        bizContent.put("subject", aliPayParam.getSubject());
        //手机网站支付默认传值FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "QUICK_WAP_WAY");
        request.setBizContent(bizContent.toString());
        String formHtml = null;
        try {
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return formHtml;
    }
}
