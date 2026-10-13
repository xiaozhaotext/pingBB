package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dao.OmsOrderOperateHistoryDao;
import com.macro.mall.dto.*;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderOperateHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderOperateHistory;
import com.macro.mall.service.OmsOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单管理Service实现类
 * Created by macro on 2018/10/11.
 */
@Service
public class OmsOrderServiceImpl implements OmsOrderService {
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderDao orderDao;
    @Autowired
    private OmsOrderOperateHistoryDao orderOperateHistoryDao;
    @Autowired
    private OmsOrderOperateHistoryMapper orderOperateHistoryMapper;

    @Override
    public List<OmsOrder> list(OmsOrderQueryParam queryParam, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        return orderDao.getList(queryParam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delivery(List<OmsOrderDeliveryParam> deliveryParamList) {
        List<Long> orderIds = validateDelivery(deliveryParamList);
        //只能在待发货状态发货，未支付、已发货、已完成、已关闭订单一律拒绝，避免状态机被改坏。
        requireOrderStatus(orderIds, List.of(1), "仅待发货订单可以发货");
        int count = orderDao.delivery(deliveryParamList);
        if (count != orderIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "发货未全部成功，订单状态可能已变化，请刷新后重试");
        }
        List<OmsOrderOperateHistory> operateHistoryList = deliveryParamList.stream()
                .map(omsOrderDeliveryParam -> {
                    OmsOrderOperateHistory history = new OmsOrderOperateHistory();
                    history.setOrderId(omsOrderDeliveryParam.getOrderId());
                    history.setCreateTime(new Date());
                    history.setOperateMan("后台管理员");
                    history.setOrderStatus(2);
                    history.setNote("完成发货:" + omsOrderDeliveryParam.getDeliveryCompany()
                            + " " + omsOrderDeliveryParam.getDeliverySn());
                    return history;
                }).collect(Collectors.toList());
        orderOperateHistoryDao.insertList(operateHistoryList);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int close(List<Long> ids, String note) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要关闭的订单");
        }
        //已发货、已完成、已关闭的订单不能再次关闭，否则会覆盖真实的履约结果。
        requireOrderStatus(ids, List.of(0, 1), "仅未支付或待发货订单可以关闭");
        OmsOrder record = new OmsOrder();
        record.setStatus(4);
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andIdIn(ids).andStatusIn(List.of(0, 1));
        int count = orderMapper.updateByExampleSelective(record, example);
        if (count != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关闭未全部成功，订单状态可能已变化，请刷新后重试");
        }
        List<OmsOrderOperateHistory> historyList = ids.stream().map(orderId -> {
            OmsOrderOperateHistory history = new OmsOrderOperateHistory();
            history.setOrderId(orderId);
            history.setCreateTime(new Date());
            history.setOperateMan("后台管理员");
            history.setOrderStatus(4);
            history.setNote("订单关闭:"+note);
            return history;
        }).collect(Collectors.toList());
        orderOperateHistoryDao.insertList(historyList);
        return count;
    }

    /** 校验订单存在且处于允许的状态，不满足时整批回滚。 */
    private void requireOrderStatus(List<Long> orderIds, List<Integer> allowedStatus, String message) {
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andIdIn(orderIds).andDeleteStatusEqualTo(0);
        Map<Long, Integer> statusById = new HashMap<>();
        for (OmsOrder order : orderMapper.selectByExample(example)) {
            statusById.put(order.getId(), order.getStatus());
        }
        for (Long orderId : orderIds) {
            if (!statusById.containsKey(orderId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在或已删除: " + orderId);
            }
            Integer current = statusById.get(orderId);
            if (current == null || !allowedStatus.contains(current)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "订单 " + orderId + " 当前状态不允许该操作：" + message);
            }
        }
    }

    /** 发货参数校验：必须给出物流公司与单号，且同一订单不能在一批里重复出现。 */
    private List<Long> validateDelivery(List<OmsOrderDeliveryParam> deliveryParamList) {
        if (CollectionUtils.isEmpty(deliveryParamList)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要发货的订单");
        }
        List<Long> orderIds = new ArrayList<>();
        for (OmsOrderDeliveryParam param : deliveryParamList) {
            if (param.getOrderId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发货参数缺少订单编号");
            }
            if (!StringUtils.hasText(param.getDeliveryCompany()) || !StringUtils.hasText(param.getDeliverySn())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发货必须同时填写物流公司和物流单号");
            }
            if (orderIds.contains(param.getOrderId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一订单不能重复发货");
            }
            orderIds.add(param.getOrderId());
        }
        return orderIds;
    }

    @Override
    public int delete(List<Long> ids) {
        OmsOrder record = new OmsOrder();
        record.setDeleteStatus(1);
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andIdIn(ids);
        return orderMapper.updateByExampleSelective(record, example);
    }

    @Override
    public OmsOrderDetail detail(Long id) {
        return orderDao.getDetail(id);
    }

    @Override
    public int updateReceiverInfo(OmsReceiverInfoParam receiverInfoParam) {
        OmsOrder order = new OmsOrder();
        order.setId(receiverInfoParam.getOrderId());
        order.setReceiverName(receiverInfoParam.getReceiverName());
        order.setReceiverPhone(receiverInfoParam.getReceiverPhone());
        order.setReceiverPostCode(receiverInfoParam.getReceiverPostCode());
        order.setReceiverDetailAddress(receiverInfoParam.getReceiverDetailAddress());
        order.setReceiverProvince(receiverInfoParam.getReceiverProvince());
        order.setReceiverCity(receiverInfoParam.getReceiverCity());
        order.setReceiverRegion(receiverInfoParam.getReceiverRegion());
        order.setModifyTime(new Date());
        int count = orderMapper.updateByPrimaryKeySelective(order);
        //插入操作记录
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(receiverInfoParam.getOrderId());
        history.setCreateTime(new Date());
        history.setOperateMan("后台管理员");
        history.setOrderStatus(receiverInfoParam.getStatus());
        history.setNote("修改收货人信息");
        orderOperateHistoryMapper.insert(history);
        return count;
    }

    @Override
    public int updateMoneyInfo(OmsMoneyInfoParam moneyInfoParam) {
        OmsOrder order = new OmsOrder();
        order.setId(moneyInfoParam.getOrderId());
        order.setFreightAmount(moneyInfoParam.getFreightAmount());
        order.setDiscountAmount(moneyInfoParam.getDiscountAmount());
        order.setModifyTime(new Date());
        int count = orderMapper.updateByPrimaryKeySelective(order);
        //插入操作记录
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(moneyInfoParam.getOrderId());
        history.setCreateTime(new Date());
        history.setOperateMan("后台管理员");
        history.setOrderStatus(moneyInfoParam.getStatus());
        history.setNote("修改费用信息");
        orderOperateHistoryMapper.insert(history);
        return count;
    }

    @Override
    public int updateNote(Long id, String note, Integer status) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setNote(note);
        order.setModifyTime(new Date());
        int count = orderMapper.updateByPrimaryKeySelective(order);
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setOrderId(id);
        history.setCreateTime(new Date());
        history.setOperateMan("后台管理员");
        history.setOrderStatus(status);
        history.setNote("修改备注信息："+note);
        orderOperateHistoryMapper.insert(history);
        return count;
    }
}
