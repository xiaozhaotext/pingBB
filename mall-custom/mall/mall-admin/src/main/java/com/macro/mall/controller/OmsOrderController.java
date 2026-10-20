package com.macro.mall.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.dto.*;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.service.OmsOrderService;
import com.macro.mall.service.LogisticsTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单管理Controller
 * Created by macro on 2018/10/11.
 */
@Controller
@Tag(name = "OmsOrderController", description = "订单管理")
@RequestMapping("/order")
public class OmsOrderController {
    @Autowired
    private OmsOrderService orderService;
    @Autowired
    private LogisticsTrackService logisticsTrackService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "查询订单物流轨迹")
    @RequestMapping(value = "/logistics", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<LogisticsTrackService.TrackResult> logistics(@RequestParam String deliveryCompany,
                                                                     @RequestParam String deliverySn,
                                                                     @RequestParam(required = false) String receiverPhone) {
        return CommonResult.success(logisticsTrackService.query(deliveryCompany, deliverySn, receiverPhone));
    }

    @Operation(summary = "查询订单")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<OmsOrder>> list(OmsOrderQueryParam queryParam,
                                                   @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize,
                                                   @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        List<OmsOrder> orderList = orderService.list(queryParam, pageSize, pageNum);
        return CommonResult.success(CommonPage.restPage(orderList));
    }

    @Operation(summary = "批量发货")
    @RequestMapping(value = "/update/delivery", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delivery(@RequestBody List<OmsOrderDeliveryParam> deliveryParamList) {
        int count = orderService.delivery(deliveryParamList);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "批量关闭订单")
    @RequestMapping(value = "/update/close", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult close(@RequestParam("ids") List<Long> ids, @RequestParam String note) {
        int count = orderService.close(ids, note);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "批量删除订单")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@RequestParam("ids") List<Long> ids) {
        int count = orderService.delete(ids);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "获取订单详情：订单信息、商品信息、操作记录")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<OmsOrderDetail> detail(@PathVariable Long id) {
        OmsOrderDetail orderDetailResult = orderService.detail(id);
        return CommonResult.success(orderDetailResult);
    }

    @Operation(summary = "修改收货人信息")
    @RequestMapping(value = "/update/receiverInfo", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateReceiverInfo(@RequestBody OmsReceiverInfoParam receiverInfoParam) {
        int count = orderService.updateReceiverInfo(receiverInfoParam);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "修改订单费用信息")
    @RequestMapping(value = "/update/moneyInfo", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateReceiverInfo(@RequestBody OmsMoneyInfoParam moneyInfoParam) {
        int count = orderService.updateMoneyInfo(moneyInfoParam);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "订单销量汇总")
    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalOrderCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0"));
        data.put("paidOrderCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3)"));
        data.put("pendingPaymentCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 0"));
        data.put("pendingDeliveryCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 1"));
        data.put("pendingReceiveCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 2"));
        data.put("completedCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 3"));
        data.put("closedCount", scalar("SELECT COUNT(*) FROM oms_order WHERE delete_status = 0 AND status = 4"));
        data.put("returnApplyCount", scalar("SELECT COUNT(*) FROM oms_order_return_apply"));
        data.put("totalSalesAmount", money("SELECT COALESCE(SUM(pay_amount+COALESCE(balance_amount,0)),0) FROM oms_order WHERE delete_status = 0 AND status IN (1,2,3) AND payment_time IS NOT NULL"));
        data.put("totalProductQuantity", scalar("SELECT COALESCE(SUM(i.product_quantity),0) FROM oms_order_item i JOIN oms_order o ON o.id = i.order_id WHERE o.delete_status = 0 AND o.status IN (1,2,3)"));
        return CommonResult.success(data);
    }

    private long scalar(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal money(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    @Operation(summary = "备注订单")
    @RequestMapping(value = "/update/note", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateNote(@RequestParam("id") Long id,
                                   @RequestParam("note") String note,
                                   @RequestParam("status") Integer status) {
        int count = orderService.updateNote(id, note, status);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }
}
