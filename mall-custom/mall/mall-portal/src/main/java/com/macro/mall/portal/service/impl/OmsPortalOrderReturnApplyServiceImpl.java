package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderItemExample;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 订单退货管理Service实现类
 * Created by macro on 2018/10/17.
 */
@Service
public class OmsPortalOrderReturnApplyServiceImpl implements OmsPortalOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private UmsMemberService memberService;
    @Override
    public int create(OmsOrderReturnApplyParam returnApply) {
        if (returnApply.getOrderId() == null || returnApply.getProductId() == null
                || returnApply.getProductCount() == null || returnApply.getProductCount() <= 0) {
            Asserts.fail("退货申请参数无效");
        }
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(returnApply.getOrderId());
        if (order == null || !member.getId().equals(order.getMemberId())
                || order.getStatus() == null || order.getStatus() < 1 || order.getStatus() > 3) {
            Asserts.fail("订单不存在或无权申请售后");
        }
        OmsOrderItemExample example = new OmsOrderItemExample();
        example.createCriteria().andOrderIdEqualTo(order.getId()).andProductIdEqualTo(returnApply.getProductId());
        List<OmsOrderItem> items = orderItemMapper.selectByExample(example);
        if (items.size() != 1 || returnApply.getProductCount() > items.get(0).getProductQuantity()) {
            Asserts.fail("退货商品不属于订单或数量无效");
        }
        OmsOrderItem item = items.get(0);
        // 同一订单同一商品不允许同时存在多笔未结案申请，避免后台重复退款和重复归还库存。
        OmsOrderReturnApplyExample pendingExample = new OmsOrderReturnApplyExample();
        pendingExample.createCriteria().andOrderIdEqualTo(order.getId())
                .andProductIdEqualTo(returnApply.getProductId()).andStatusIn(List.of(0, 1));
        if (returnApplyMapper.countByExample(pendingExample) > 0) {
            Asserts.fail("该商品已有售后申请正在处理中，请勿重复提交");
        }
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply,realApply);
        // 商品、订单与价格快照只能来自数据库，不信任请求体中的字段。
        realApply.setOrderSn(order.getOrderSn());
        realApply.setMemberUsername(order.getMemberUsername());
        realApply.setProductPic(item.getProductPic());
        realApply.setProductName(item.getProductName());
        realApply.setProductBrand(item.getProductBrand());
        realApply.setProductAttr(item.getProductAttr());
        realApply.setProductPrice(item.getProductPrice());
        realApply.setProductRealPrice(item.getRealAmount());
        realApply.setCreateTime(new Date());
        realApply.setStatus(0);
        return returnApplyMapper.insert(realApply);
    }
}
