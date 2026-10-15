package com.macro.mall.portal.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 前台订单管理Service
 * Created by macro on 2018/8/30.
 */
@Service
public class OmsPortalOrderServiceImpl implements OmsPortalOrderService {
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsCartItemService cartItemService;
    @Autowired
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Autowired
    private UmsMemberCouponService memberCouponService;
    @Autowired
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private SmsCouponHistoryDao couponHistoryDao;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private PortalOrderItemDao orderItemDao;
    @Autowired
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Autowired
    private RedisService redisService;
    @Autowired
    private PointsWalletService pointsWalletService;
    @Value("${redis.key.orderId}")
    private String REDIS_KEY_ORDER_ID;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${mall.payment.alipay.enabled:false}")
    private boolean alipayEnabled;
    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private OmsOrderSettingMapper orderSettingMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;
    @Autowired
    private CancelOrderSender cancelOrderSender;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ShareOrderBonusService shareOrderBonusService;

    @Override
    public ConfirmOrderResult generateConfirmOrder(List<Long> cartIds) {
        ConfirmOrderResult result = new ConfirmOrderResult();
        //获取购物车信息
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(),cartIds);
        result.setCartPromotionItemList(cartPromotionItemList);
        //获取用户收货地址列表
        List<UmsMemberReceiveAddress> memberReceiveAddressList = memberReceiveAddressService.list();
        result.setMemberReceiveAddressList(memberReceiveAddressList);
        //获取用户可用优惠券列表
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        result.setCouponHistoryDetailList(couponHistoryDetailList);
        //获取用户积分
        com.macro.mall.portal.domain.UmsPointsAccount productAccount =
                pointsWalletService.getAccount(currentMember.getId(), PointsWalletService.TYPE_PRODUCT);
        long productBalance = productAccount == null || productAccount.getAvailable() == null
                ? 0 : productAccount.getAvailable();
        result.setMemberIntegration((int) Math.min(productBalance, Integer.MAX_VALUE));
        result.setProductPointYuanPerPoint(productPointYuanPerPoint());
        result.setProductPointMaxRatio(productPointMaxRatio());
        com.macro.mall.portal.domain.UmsPointsAccount repurchaseAccount =
                pointsWalletService.getAccount(currentMember.getId(), PointsWalletService.TYPE_REPURCHASE);
        result.setRepurchasePointsBalance(repurchaseAccount == null || repurchaseAccount.getAvailable() == null
                ? 0 : repurchaseAccount.getAvailable());
        result.setRebuyPointsRatio(repurchasePointsRatio());
        result.setRebuyEligible(!cartPromotionItemList.isEmpty() && cartPromotionItemList.stream().allMatch(item -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pms_product_zone WHERE zone_code='REBUY' AND product_id=?",
                    Integer.class, item.getProductId());
            return count != null && count > 0;
        }));
        boolean consumeOnly = consumptionCart(cartPromotionItemList.stream().map(CartPromotionItem::getProductId).collect(java.util.stream.Collectors.toList()));
        result.setConsumeOnly(consumeOnly);
        if (consumeOnly) result.setProductPointMaxRatio(BigDecimal.ONE);
        //获取积分使用规则
        UmsIntegrationConsumeSetting integrationConsumeSetting = integrationConsumeSettingMapper.selectByPrimaryKey(1L);
        result.setIntegrationConsumeSetting(integrationConsumeSetting);
        //计算总金额、活动优惠、应付金额
        ConfirmOrderResult.CalcAmount calcAmount = calcCartAmount(cartPromotionItemList);
        result.setCalcAmount(calcAmount);
        return result;
    }

    @Override
    public Map<String, Object> generateOrder(OrderParam orderParam, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{16,100}")) {
            Asserts.fail("Idempotency-Key 格式无效");
        }
        UmsMember requestMember = memberService.getCurrentMember();
        String requestHash = DigestUtil.sha256Hex(JSONUtil.toJsonStr(orderParam));
        try {
            jdbcTemplate.update("INSERT INTO oms_order_request(member_id,idempotency_key,request_hash,state) VALUES(?,?,?,'CREATING')",
                    requestMember.getId(), idempotencyKey, requestHash);
        } catch (DuplicateKeyException duplicate) {
            List<Map<String, Object>> claims = jdbcTemplate.queryForList(
                    "SELECT request_hash,order_id,state FROM oms_order_request WHERE member_id=? AND idempotency_key=?",
                    requestMember.getId(), idempotencyKey);
            if (claims.size() != 1 || !requestHash.equals(claims.get(0).get("request_hash"))) {
                Asserts.fail("幂等键已用于不同请求");
            }
            Object existingId = claims.get(0).get("order_id");
            if (existingId == null || !"COMPLETED".equals(claims.get(0).get("state"))) {
                Asserts.fail("原下单请求仍在处理中，请稍后查询");
            }
            Long orderId = ((Number) existingId).longValue();
            OmsOrder existingOrder = orderMapper.selectByPrimaryKey(orderId);
            if (existingOrder == null || !requestMember.getId().equals(existingOrder.getMemberId())) {
                Asserts.fail("原订单不存在或无权访问");
            }
            OmsOrderItemExample itemsExample = new OmsOrderItemExample();
            itemsExample.createCriteria().andOrderIdEqualTo(orderId);
            Map<String, Object> replay = new HashMap<>();
            replay.put("order", existingOrder);
            replay.put("orderItemList", orderItemMapper.selectByExample(itemsExample));
            return replay;
        }
        List<OmsOrderItem> orderItemList = new ArrayList<>();
        //校验收货地址
        if(orderParam.getMemberReceiveAddressId()==null){
            Asserts.fail("请选择收货地址！");
        }
        //获取购物车及优惠信息
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(), orderParam.getCartIds());
        if (CollectionUtils.isEmpty(cartPromotionItemList)) {
            Asserts.fail("购物车中没有可下单商品");
        }
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            //生成下单商品信息
            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductId(cartPromotionItem.getProductId());
            orderItem.setProductName(cartPromotionItem.getProductName());
            orderItem.setProductPic(cartPromotionItem.getProductPic());
            orderItem.setProductAttr(cartPromotionItem.getProductAttr());
            orderItem.setProductBrand(cartPromotionItem.getProductBrand());
            orderItem.setProductSn(cartPromotionItem.getProductSn());
            orderItem.setProductPrice(cartPromotionItem.getPrice());
            orderItem.setProductQuantity(cartPromotionItem.getQuantity());
            orderItem.setProductSkuId(cartPromotionItem.getProductSkuId());
            orderItem.setProductSkuCode(cartPromotionItem.getProductSkuCode());
            orderItem.setProductCategoryId(cartPromotionItem.getProductCategoryId());
            orderItem.setPromotionAmount(cartPromotionItem.getReduceAmount());
            orderItem.setPromotionName(cartPromotionItem.getPromotionMessage());
            orderItem.setGiftIntegration(cartPromotionItem.getIntegration());
            orderItem.setGiftGrowth(cartPromotionItem.getGrowth());
            orderItemList.add(orderItem);
        }
        //判断购物车中商品是否都有库存
        if (!hasStock(cartPromotionItemList)) {
            Asserts.fail("库存不足，无法下单");
        }
        //判断使用使用了优惠券
        if (orderParam.getCouponId() == null) {
            //不用优惠券
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setCouponAmount(new BigDecimal(0));
            }
        } else {
            //使用优惠券
            SmsCouponHistoryDetail couponHistoryDetail = getUseCoupon(cartPromotionItemList, orderParam.getCouponId());
            if (couponHistoryDetail == null) {
                Asserts.fail("该优惠券不可用");
            }
            //对下单商品的优惠券进行处理
            handleCouponAmount(orderItemList, couponHistoryDetail);
        }
        boolean consumeOnly = consumptionCart(orderItemList.stream().map(OmsOrderItem::getProductId).collect(java.util.stream.Collectors.toList()));
        if (consumeOnly) {
            if (orderParam.getUseRepurchasePoints() != null && orderParam.getUseRepurchasePoints() > 0) Asserts.fail("消费区仅可使用商品积分");
            BigDecimal net = calcTotalAmount(orderItemList).subtract(calcPromotionAmount(orderItemList)).subtract(calcCouponAmount(orderItemList));
            int required;
            try { required = net.divide(productPointYuanPerPoint()).intValueExact(); }
            catch (ArithmeticException ex) { throw new com.macro.mall.common.exception.ApiException("消费区金额无法按当前积分兑换比例精确兑换，请联系商家"); }
            if (required <= 0 || orderParam.getUseIntegration() == null || orderParam.getUseIntegration() != required) Asserts.fail("消费区必须使用100%商品积分兑换");
        }
        if (orderParam.getUseIntegration() != null && orderParam.getUseIntegration() > 0
                && orderParam.getUseRepurchasePoints() != null && orderParam.getUseRepurchasePoints() > 0) {
            Asserts.fail("产品积分与复购积分不能在同一订单叠加使用");
        }
        //判断是否使用产品积分
        if (orderParam.getUseIntegration() == null||orderParam.getUseIntegration().equals(0)) {
            //不使用积分
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setIntegrationAmount(new BigDecimal(0));
            }
        } else {
            //使用积分
            BigDecimal integrationAmount = getUseIntegrationAmount(orderParam.getUseIntegration(), orderItemList, currentMember);
            allocateProductPointDiscount(orderItemList, integrationAmount);
        }
        //计算order_item的实付金额前：处理复购区30%复购积分+70%现金
        BigDecimal repurchaseDeduction = handleRepurchasePoints(orderParam, orderItemList, currentMember);
        //计算order_item的实付金额
        handleRealAmount(orderItemList);
        //进行库存锁定
        lockStock(cartPromotionItemList);
        //根据商品合计、运费、活动优惠、优惠券、积分计算应付金额
        OmsOrder order = new OmsOrder();
        order.setDiscountAmount(new BigDecimal(0));
        order.setTotalAmount(calcTotalAmount(orderItemList));
        order.setFreightAmount(new BigDecimal(0));
        order.setPromotionAmount(calcPromotionAmount(orderItemList));
        order.setPromotionInfo(getOrderPromotionInfo(orderItemList));
        if (orderParam.getCouponId() == null) {
            order.setCouponAmount(new BigDecimal(0));
        } else {
            order.setCouponId(orderParam.getCouponId());
            order.setCouponAmount(calcCouponAmount(orderItemList));
        }
        if (orderParam.getUseIntegration() == null) {
            order.setIntegration(0);
            order.setIntegrationAmount(new BigDecimal(0));
        } else {
            order.setIntegration(orderParam.getUseIntegration());
            order.setIntegrationAmount(calcIntegrationAmount(orderItemList));
        }
        order.setPayAmount(calcPayAmount(order));
        if (order.getPayAmount().signum() < 0) Asserts.fail("订单应付金额不能小于 0");
        order.setUseIntegration(orderParam.getUseIntegration() == null ? 0 : orderParam.getUseIntegration());
        //转化为订单信息并插入数据库
        order.setMemberId(currentMember.getId());
        order.setCreateTime(new Date());
        order.setMemberUsername(currentMember.getUsername());
        //支付方式：0->未支付；1->支付宝；2->微信
        order.setPayType(orderParam.getPayType());
        //订单来源：0->PC订单；1->app订单
        order.setSourceType(1);
        //订单状态：0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单
        order.setStatus(0);
        //订单类型：0->正常订单；1->秒杀订单
        order.setOrderType(0);
        //收货人信息：姓名、电话、邮编、地址
        UmsMemberReceiveAddress address = memberReceiveAddressService.getItem(orderParam.getMemberReceiveAddressId());
        if (address == null) {
            Asserts.fail("收货地址不存在或不属于当前用户");
        }
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setReceiverPostCode(address.getPostCode());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverRegion(address.getRegion());
        order.setReceiverDetailAddress(address.getDetailAddress());
        //0->未确认；1->已确认
        order.setConfirmStatus(0);
        order.setDeleteStatus(0);
        //计算赠送积分
        order.setIntegration(calcGifIntegration(orderItemList));
        //计算赠送成长值
        order.setGrowth(calcGiftGrowth(orderItemList));
        //生成订单号
        order.setOrderSn(generateOrderSn(order));
        //设置自动收货天数
        List<OmsOrderSetting> orderSettings = orderSettingMapper.selectByExample(new OmsOrderSettingExample());
        if(CollUtil.isNotEmpty(orderSettings)){
            order.setAutoConfirmDay(orderSettings.get(0).getConfirmOvertime());
        }
        // TODO: 2018/9/3 bill_*,delivery_*
        //插入order表和order_item表
        orderMapper.insert(order);
        if (order.getUseIntegration() != null && order.getUseIntegration() > 0) {
            consumeOrderPoints(order.getId(), currentMember.getId(), PointsWalletService.TYPE_PRODUCT,
                    "PRODUCT", order.getUseIntegration(), order.getIntegrationAmount());
        }
        if (orderParam.getUseRepurchasePoints() != null && orderParam.getUseRepurchasePoints() > 0) {
            consumeOrderPoints(order.getId(), currentMember.getId(), PointsWalletService.TYPE_REPURCHASE,
                    "REPURCHASE", orderParam.getUseRepurchasePoints(), repurchaseDeduction);
        }
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemDao.insertList(orderItemList);
        if (jdbcTemplate.update("UPDATE oms_order_request SET order_id=?,state='COMPLETED' WHERE member_id=? AND idempotency_key=? AND state='CREATING'",
                order.getId(), requestMember.getId(), idempotencyKey) != 1) {
            Asserts.fail("幂等请求记录异常");
        }
        //如使用优惠券更新优惠券使用状态
        if (orderParam.getCouponId() != null) {
            updateCouponStatus(orderParam.getCouponId(), currentMember.getId(), 1);
        }
        //如使用积分需要扣除积分
        //删除购物车中的下单商品
        deleteCartItemList(cartPromotionItemList, currentMember);
        if (consumeOnly) {
            if (order.getPayAmount().signum() != 0) Asserts.fail("消费区订单不可存在现金应付金额");
            // Points were already debited in this transaction. Complete fulfillment without an external payment request.
            paySuccess(order.getId(), 3);
            order = orderMapper.selectByPrimaryKey(order.getId());
        } else {
            sendDelayMessageCancelOrder(order.getId());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);
        return result;
    }

    @Override
    public Integer paySuccess(Long orderId, Integer payType) {
        if (orderId == null || payType == null || (payType != 1 && payType != 2 && payType != 3)) {
            Asserts.fail("支付参数无效");
        }
        // 条件更新是状态转换和支付/超时取消竞争的唯一入口。
        if (portalOrderDao.markPaidIfPending(orderId, payType) != 1) {
            OmsOrder existing = orderMapper.selectByPrimaryKey(orderId);
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() >= 1 && existing.getStatus() <= 3) {
                return 0; // 已确认过的通知幂等返回，不重复扣库存。
            }
            Asserts.fail("订单不在待支付状态，需人工核查迟到支付");
        }
        OmsOrderDetail orderDetail = portalOrderDao.getDetail(orderId);
        if (orderDetail == null || CollectionUtils.isEmpty(orderDetail.getOrderItemList())) {
            Asserts.fail("订单商品不存在");
        }
        for (OmsOrderItem item : orderDetail.getOrderItemList()) {
            if (portalOrderDao.consumeReservedSku(item.getProductSkuId(), item.getProductQuantity()) != 1) {
                Asserts.fail("库存预占异常，支付确认已回滚");
            }
        }
        // 分享下单奖励：给该会员的直推分享人入账（幂等，规则与开关由后台配置）
        shareOrderBonusService.postForOrder(orderId, orderDetail.getMemberId(), orderDetail.getPayAmount());
        // 购买赠送产品积分：按订单 giftIntegration 入账到买家产品积分钱包
        grantOrderGiftPoints(orderId, orderDetail);
        return orderDetail.getOrderItemList().size();
    }

    /**
     * 订单支付成功后，给买家发放商品赠送的产品积分。
     * 幂等：用 order:gift:{orderId} 做幂等键，重复调用不重复发放。
     */
    private void grantOrderGiftPoints(Long orderId, com.macro.mall.portal.domain.OmsOrderDetail orderDetail) {
        int giftPoints = 0;
        for (com.macro.mall.model.OmsOrderItem item : orderDetail.getOrderItemList()) {
            if (item.getGiftIntegration() != null) {
                giftPoints += item.getGiftIntegration() * (item.getProductQuantity() == null ? 1 : item.getProductQuantity());
            }
        }
        if (giftPoints <= 0) return;
        Long memberId = orderDetail.getMemberId();
        String idempotency = "order:gift:" + orderId;
        com.macro.mall.common.api.CommonResult<Void> result = pointsWalletService.changePoints(
                memberId, PointsWalletService.TYPE_PRODUCT, "earn", (long) giftPoints,
                orderId.toString(), idempotency, null, "商品购买赠送");
        if (result.getCode() != 200) {
            throw new IllegalStateException("购买赠送积分入账失败: " + result.getMessage());
        }
        // 同步到 ums_member.integration，复用下单抵扣链路
        memberService.updateIntegration(memberId,
                memberService.getById(memberId).getIntegration() + giftPoints);
    }

    @Override
    public Integer cancelTimeOutOrder() {
        if (alipayEnabled) {
            // 尚无可靠的支付渠道查单/关单能力，不能释放可能已支付的库存。
            return 0;
        }
        Integer count=0;
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);
        //查询超时、未支付的订单及订单详情
        List<OmsOrderDetail> timeOutOrders = portalOrderDao.getTimeOutOrders(orderSetting.getNormalOrderOvertime());
        if (CollectionUtils.isEmpty(timeOutOrders)) {
            return count;
        }
        for (OmsOrderDetail timeOutOrder : timeOutOrders) {
            cancelOrder(timeOutOrder.getId());
        }
        return timeOutOrders.size();
    }

    @Override
    public void cancelOrder(Long orderId) {
        if (alipayEnabled) {
            return; // 延迟消息不得与未确认的渠道支付争抢库存预占。
        }
        //查询未付款的取消订单
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andIdEqualTo(orderId).andStatusEqualTo(0).andDeleteStatusEqualTo(0);
        List<OmsOrder> cancelOrderList = orderMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(cancelOrderList)) {
            return;
        }
        OmsOrder cancelOrder = cancelOrderList.get(0);
        if (cancelOrder != null) {
            if (portalOrderDao.markCanceledIfPending(orderId) != 1) {
                return; // 支付确认先提交时不释放库存。
            }
            OmsOrderItemExample orderItemExample = new OmsOrderItemExample();
            orderItemExample.createCriteria().andOrderIdEqualTo(orderId);
            List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(orderItemExample);
            //解除订单商品库存锁定
            if (!CollectionUtils.isEmpty(orderItemList)) {
                for (OmsOrderItem item : orderItemList) {
                    if (portalOrderDao.releaseReservedSku(item.getProductSkuId(), item.getProductQuantity()) != 1) {
                        Asserts.fail("库存预占异常，取消操作已回滚");
                    }
                }
            }
            //修改优惠券使用状态
            updateCouponStatus(cancelOrder.getCouponId(), cancelOrder.getMemberId(), 0);
            refundOrderPoints(cancelOrder);
        }
    }

    @Override
    public void cancelUserOrder(Long orderId) {
        if (alipayEnabled) {
            Asserts.fail("支付渠道启用时须先确认渠道关单结果");
        }
        UmsMember member = memberService.getCurrentMember();
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null || !member.getId().equals(order.getMemberId())) {
            Asserts.fail("订单不存在或无权操作");
        }
        if (!Integer.valueOf(0).equals(order.getStatus()) || !Integer.valueOf(0).equals(order.getDeleteStatus())) {
            Asserts.fail("仅待付款订单可以取消");
        }
        cancelOrder(orderId);
        OmsOrder current = orderMapper.selectByPrimaryKey(orderId);
        if (current == null || !Integer.valueOf(4).equals(current.getStatus())) {
            Asserts.fail("订单状态已变化，取消失败");
        }
    }

    @Override
    public void sendDelayMessageCancelOrder(Long orderId) {
        //获取订单超时时间
        OmsOrderSetting orderSetting = orderSettingMapper.selectByPrimaryKey(1L);
        long delayTimes = orderSetting.getNormalOrderOvertime() * 60 * 1000;
        //发送延迟消息
        cancelOrderSender.sendMessage(orderId, delayTimes);
    }

    @Override
    public void confirmReceiveOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        if (jdbcTemplate.update("UPDATE oms_order SET status=3,confirm_status=1,receive_time=NOW() "
                + "WHERE id=? AND member_id=? AND status=2 AND delete_status=0", orderId, member.getId()) != 1) {
            Asserts.fail("订单不存在、无权操作或状态不允许确认收货");
        }
    }

    @Override
    public CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize) {
        if(status==-1){
            status = null;
        }
        UmsMember member = memberService.getCurrentMember();
        PageHelper.startPage(pageNum,pageSize);
        OmsOrderExample orderExample = new OmsOrderExample();
        OmsOrderExample.Criteria criteria = orderExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0)
                .andMemberIdEqualTo(member.getId());
        if(status!=null){
            criteria.andStatusEqualTo(status);
        }
        orderExample.setOrderByClause("create_time desc");
        List<OmsOrder> orderList = orderMapper.selectByExample(orderExample);
        CommonPage<OmsOrder> orderPage = CommonPage.restPage(orderList);
        //设置分页信息
        CommonPage<OmsOrderDetail> resultPage = new CommonPage<>();
        resultPage.setPageNum(orderPage.getPageNum());
        resultPage.setPageSize(orderPage.getPageSize());
        resultPage.setTotal(orderPage.getTotal());
        resultPage.setTotalPage(orderPage.getTotalPage());
        if(CollUtil.isEmpty(orderList)){
            return resultPage;
        }
        //设置数据信息
        List<Long> orderIds = orderList.stream().map(OmsOrder::getId).collect(Collectors.toList());
        OmsOrderItemExample orderItemExample = new OmsOrderItemExample();
        orderItemExample.createCriteria().andOrderIdIn(orderIds);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(orderItemExample);
        List<OmsOrderDetail> orderDetailList = new ArrayList<>();
        for (OmsOrder omsOrder : orderList) {
            OmsOrderDetail orderDetail = new OmsOrderDetail();
            BeanUtil.copyProperties(omsOrder,orderDetail);
            List<OmsOrderItem> relatedItemList = orderItemList.stream().filter(item -> item.getOrderId().equals(orderDetail.getId())).collect(Collectors.toList());
            orderDetail.setOrderItemList(relatedItemList);
            orderDetailList.add(orderDetail);
        }
        resultPage.setList(orderDetailList);
        return resultPage;
    }

    @Override
    public OmsOrderDetail detail(Long orderId) {
        OmsOrder omsOrder = orderMapper.selectByPrimaryKey(orderId);
        UmsMember member = memberService.getCurrentMember();
        if (omsOrder == null || !member.getId().equals(omsOrder.getMemberId())) {
            Asserts.fail("订单不存在或无权查看");
        }
        OmsOrderItemExample example = new OmsOrderItemExample();
        example.createCriteria().andOrderIdEqualTo(orderId);
        List<OmsOrderItem> orderItemList = orderItemMapper.selectByExample(example);
        OmsOrderDetail orderDetail = new OmsOrderDetail();
        BeanUtil.copyProperties(omsOrder,orderDetail);
        orderDetail.setOrderItemList(orderItemList);
        return orderDetail;
    }

    @Override
    public void deleteOrder(Long orderId) {
        UmsMember member = memberService.getCurrentMember();
        if (jdbcTemplate.update("UPDATE oms_order SET delete_status=1 WHERE id=? AND member_id=? "
                + "AND status IN (3,4) AND delete_status=0", orderId, member.getId()) != 1) {
            Asserts.fail("订单不存在、无权操作或状态不允许删除");
        }
    }

    @Override
    public void paySuccessByOrderSn(String orderSn, Integer payType) {
        OmsOrder order = getOrderByOrderSn(orderSn);
        if (order == null) {
            Asserts.fail("订单不在待支付状态，需重新查单");
        }
        this.paySuccess(order.getId(),payType);
    }

    @Override
    public OmsOrder getOrderByOrderSn(String orderSn) {
        OmsOrderExample example =  new OmsOrderExample();
        example.createCriteria()
                .andOrderSnEqualTo(orderSn)
                .andStatusEqualTo(0)
                .andDeleteStatusEqualTo(0);
        List<OmsOrder> orderList = orderMapper.selectByExample(example);
        if(CollUtil.isNotEmpty(orderList)){
            return orderList.get(0);
        }else return null;
    }

    /**
     * 生成18位订单编号:8位日期+2位平台号码+2位支付方式+6位以上自增id
     */
    private String generateOrderSn(OmsOrder order) {
        StringBuilder sb = new StringBuilder();
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = REDIS_DATABASE+":"+ REDIS_KEY_ORDER_ID + date;
        Long increment = redisService.incr(key, 1);
        sb.append(date);
        sb.append(String.format("%02d", order.getSourceType()));
        sb.append(String.format("%02d", order.getPayType()));
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }
        return sb.toString();
    }

    /**
     * 删除下单商品的购物车信息
     */
    private void deleteCartItemList(List<CartPromotionItem> cartPromotionItemList, UmsMember currentMember) {
        List<Long> ids = new ArrayList<>();
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            ids.add(cartPromotionItem.getId());
        }
        cartItemService.delete(currentMember.getId(), ids);
    }

    /**
     * 计算该订单赠送的成长值
     */
    private Integer calcGiftGrowth(List<OmsOrderItem> orderItemList) {
        Integer sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum = sum + orderItem.getGiftGrowth() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 计算该订单赠送的积分
     */
    private Integer calcGifIntegration(List<OmsOrderItem> orderItemList) {
        int sum = 0;
        for (OmsOrderItem orderItem : orderItemList) {
            sum += orderItem.getGiftIntegration() * orderItem.getProductQuantity();
        }
        return sum;
    }

    /**
     * 将优惠券信息更改为指定状态
     *
     * @param couponId  优惠券id
     * @param memberId  会员id
     * @param useStatus 0->未使用；1->已使用
     */
    private void updateCouponStatus(Long couponId, Long memberId, Integer useStatus) {
        if (couponId == null) return;
        //查询第一张优惠券
        SmsCouponHistoryExample example = new SmsCouponHistoryExample();
        example.createCriteria().andMemberIdEqualTo(memberId)
                .andCouponIdEqualTo(couponId).andUseStatusEqualTo(useStatus == 0 ? 1 : 0);
        List<SmsCouponHistory> couponHistoryList = couponHistoryMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(couponHistoryList)) {
            SmsCouponHistory couponHistory = couponHistoryList.get(0);
            couponHistory.setUseTime(new Date());
            couponHistory.setUseStatus(useStatus);
            couponHistoryMapper.updateByPrimaryKeySelective(couponHistory);
        }
    }

    private void handleRealAmount(List<OmsOrderItem> orderItemList) {
        for (OmsOrderItem orderItem : orderItemList) {
            //原价-促销优惠-优惠券抵扣-积分抵扣
            BigDecimal realAmount = orderItem.getProductPrice()
                    .subtract(orderItem.getPromotionAmount())
                    .subtract(orderItem.getCouponAmount())
                    .subtract(orderItem.getIntegrationAmount());
            orderItem.setRealAmount(realAmount);
        }
    }

    /**
     * 获取订单促销信息
     */
    private String getOrderPromotionInfo(List<OmsOrderItem> orderItemList) {
        StringBuilder sb = new StringBuilder();
        for (OmsOrderItem orderItem : orderItemList) {
            sb.append(orderItem.getPromotionName());
            sb.append(";");
        }
        String result = sb.toString();
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 计算订单应付金额
     */
    private BigDecimal calcPayAmount(OmsOrder order) {
        //总金额+运费-促销优惠-优惠券优惠-积分抵扣
        BigDecimal payAmount = order.getTotalAmount()
                .add(order.getFreightAmount())
                .subtract(order.getPromotionAmount())
                .subtract(order.getCouponAmount())
                .subtract(order.getIntegrationAmount());
        return payAmount;
    }

    /**
     * 计算订单优惠券金额
     */
    private BigDecimal calcIntegrationAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal integrationAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getIntegrationAmount() != null) {
                integrationAmount = integrationAmount.add(orderItem.getIntegrationAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return integrationAmount;
    }

    /**
     * 计算订单优惠券金额
     */
    private BigDecimal calcCouponAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal couponAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getCouponAmount() != null) {
                couponAmount = couponAmount.add(orderItem.getCouponAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return couponAmount;
    }

    /**
     * 计算订单活动优惠
     */
    private BigDecimal calcPromotionAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal promotionAmount = new BigDecimal(0);
        for (OmsOrderItem orderItem : orderItemList) {
            if (orderItem.getPromotionAmount() != null) {
                promotionAmount = promotionAmount.add(orderItem.getPromotionAmount().multiply(new BigDecimal(orderItem.getProductQuantity())));
            }
        }
        return promotionAmount;
    }

    private BigDecimal configuredPointDecimal(String key, BigDecimal fallback, boolean ratio) {
        try {
            String raw = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=?", String.class, key);
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() > 0 && (!ratio || value.compareTo(BigDecimal.ONE) <= 0)) return value;
        } catch (Exception ignored) { }
        return fallback;
    }

    private boolean consumptionCart(List<Long> productIds) {
        for (Long id : productIds) {
            Integer offer = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_config WHERE config_key IN ('membership.partner.product_id','membership.cofounder.product_id') AND config_value=?", Integer.class, id.toString());
            if (offer != null && offer > 0) Asserts.fail("旅游权益商品请通过商品详情的权益购买入口下单，以确保双积分和会员权益正确发放");
        }
        int consume = 0;
        for (Long id : productIds) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pms_product_zone WHERE product_id=? AND zone_code='CONSUME'", Integer.class, id);
            if (count != null && count > 0) consume++;
        }
        if (consume > 0 && consume != productIds.size()) Asserts.fail("消费区商品请单独结算");
        return consume > 0;
    }

    private BigDecimal productPointYuanPerPoint() {
        return configuredPointDecimal("product.points.yuan_per_point", BigDecimal.ONE, false);
    }

    private BigDecimal productPointMaxRatio() {
        return configuredPointDecimal("product.points.max_ratio", BigDecimal.ONE, true);
    }

    private BigDecimal getUseIntegrationAmount(Integer useIntegration, List<OmsOrderItem> items, UmsMember member) {
        if (useIntegration == null || useIntegration <= 0) Asserts.fail("产品积分必须大于 0");
        com.macro.mall.portal.domain.UmsPointsAccount account =
                pointsWalletService.getAccount(member.getId(), PointsWalletService.TYPE_PRODUCT);
        long available = account == null || account.getAvailable() == null ? 0 : account.getAvailable();
        if (available < useIntegration) Asserts.fail("产品积分余额不足");
        BigDecimal amount = productPointYuanPerPoint().multiply(BigDecimal.valueOf(useIntegration));
        if (amount.scale() > 2) Asserts.fail("产品积分换算金额最多支持两位小数");
        BigDecimal netAmount = calcTotalAmount(items)
                .subtract(calcPromotionAmount(items)).subtract(calcCouponAmount(items));
        if (netAmount.signum() <= 0 || amount.compareTo(netAmount.multiply(consumptionCart(items.stream().map(OmsOrderItem::getProductId).collect(java.util.stream.Collectors.toList())) ? BigDecimal.ONE : productPointMaxRatio())) > 0) {
            Asserts.fail("产品积分最多抵扣当前应付商品金额");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void allocateProductPointDiscount(List<OmsOrderItem> items, BigDecimal amount) {
        BigDecimal netTotal = calcTotalAmount(items)
                .subtract(calcPromotionAmount(items)).subtract(calcCouponAmount(items));
        BigDecimal remaining = amount;
        for (int i = 0; i < items.size(); i++) {
            OmsOrderItem item = items.get(i);
            BigDecimal unitNet = item.getProductPrice().subtract(item.getPromotionAmount())
                    .subtract(item.getCouponAmount());
            BigDecimal lineNet = unitNet.multiply(BigDecimal.valueOf(item.getProductQuantity()));
            BigDecimal lineDiscount = i == items.size() - 1 ? remaining
                    : amount.multiply(lineNet).divide(netTotal, 2, RoundingMode.DOWN);
            try {
                BigDecimal perUnit = lineDiscount.divide(BigDecimal.valueOf(item.getProductQuantity()),
                        2, RoundingMode.UNNECESSARY);
                if (perUnit.signum() < 0 || perUnit.compareTo(unitNet) > 0) Asserts.fail("积分分摊金额无效");
                item.setIntegrationAmount(perUnit);
            } catch (ArithmeticException ex) {
                Asserts.fail("当前多件商品的积分金额无法按分分摊，请调整积分数或分开下单");
            }
            remaining = remaining.subtract(lineDiscount);
        }
    }

    /**
     * 复购区30%复购积分+70%现金：仅当订单商品全部来自复购区(REBUY)且传入 useRepurchasePoints>0 时生效。
     * 1复购积分=1元，最高抵扣订单金额的30%。积分通过 PointsWalletService 实时扣减。
     */
    /** 复购区积分抵扣比例，来自后台配置 rebuy.points.ratio（默认 30%）。 */
    private BigDecimal repurchasePointsRatio() {
        BigDecimal fallback = new BigDecimal("0.30");
        try {
            String raw = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key='rebuy.points.ratio'", String.class);
            if (raw != null && !raw.isBlank() && !"NULL".equalsIgnoreCase(raw.trim())) {
                BigDecimal ratio = new BigDecimal(raw.trim());
                if (ratio.signum() >= 0 && ratio.compareTo(BigDecimal.ONE) <= 0) return ratio;
            }
        } catch (Exception ignored) {
            // 配置缺失/非法时回退默认值
        }
        return fallback;
    }

    private BigDecimal handleRepurchasePoints(OrderParam orderParam, List<OmsOrderItem> orderItemList, UmsMember currentMember) {
        Long usePoints = orderParam.getUseRepurchasePoints();
        if (usePoints == null || usePoints <= 0) return BigDecimal.ZERO;
        // 校验订单商品是否全部来自复购区
        for (OmsOrderItem item : orderItemList) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pms_product_zone WHERE zone_code='REBUY' AND product_id=?",
                    Integer.class, item.getProductId());
            if (count == null || count == 0) {
                Asserts.fail("复购积分仅可用于复购区商品");
            }
        }
        BigDecimal totalAmount = calcTotalAmount(orderItemList)
                .subtract(calcPromotionAmount(orderItemList)).subtract(calcCouponAmount(orderItemList));
        if (totalAmount.signum() <= 0) Asserts.fail("复购区订单应付金额无效");
        // 抵扣比例由后台配置 rebuy.points.ratio 决定（默认 0.30 = 30% 积分 + 70% 现金）
        BigDecimal ratio = repurchasePointsRatio();
        BigDecimal maxDeduct = totalAmount.multiply(ratio).setScale(2, RoundingMode.DOWN);
        BigDecimal deductAmount = new BigDecimal(usePoints).setScale(2, RoundingMode.HALF_UP);
        if (deductAmount.compareTo(maxDeduct) > 0) {
            Asserts.fail("复购积分最多抵扣订单金额的 " + ratio.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%");
        }
        // 校验余额并扣减（幂等键与订单绑定）
        com.macro.mall.portal.domain.UmsPointsAccount account =
                pointsWalletService.getAccount(currentMember.getId(), PointsWalletService.TYPE_REPURCHASE);
        long balance = account == null || account.getAvailable() == null ? 0 : account.getAvailable();
        if (balance < usePoints) {
            Asserts.fail("复购积分余额不足");
        }
        BigDecimal beforePromotion = calcPromotionAmount(orderItemList);
        // 按商品金额比例分摊抵扣
        for (OmsOrderItem orderItem : orderItemList) {
            BigDecimal lineAmount = orderItem.getProductPrice()
                    .subtract(orderItem.getPromotionAmount()).subtract(orderItem.getCouponAmount())
                    .multiply(new BigDecimal(orderItem.getProductQuantity()));
            BigDecimal perAmount = lineAmount.divide(totalAmount, 6, RoundingMode.HALF_EVEN)
                    .multiply(deductAmount).divide(BigDecimal.valueOf(orderItem.getProductQuantity()),
                            2, RoundingMode.HALF_UP);
            // 复用 promotionAmount 字段记录复购积分抵扣（前端展示为优惠）
            orderItem.setPromotionAmount(orderItem.getPromotionAmount().add(perAmount));
        }
        if (calcPromotionAmount(orderItemList).subtract(beforePromotion).compareTo(deductAmount) != 0) {
            Asserts.fail("复购积分金额无法按分分摊，请调整购买数量");
        }
        return deductAmount;
    }

    private void consumeOrderPoints(long orderId, long memberId, int walletType, String pointType,
                                    long points, BigDecimal amount) {
        com.macro.mall.common.api.CommonResult<Void> changed = pointsWalletService.changePoints(
                memberId, walletType, "consume", -points, Long.toString(orderId),
                "order:points:" + orderId + ":" + pointType, null, "商品订单积分抵扣");
        if (changed.getCode() != 200) Asserts.fail("积分扣减失败: " + changed.getMessage());
        com.macro.mall.portal.domain.UmsPointsAccount account = pointsWalletService.getAccount(memberId, walletType);
        if (account == null || account.getBalance() == null) Asserts.fail("积分扣减后余额读取失败");
        jdbcTemplate.update("INSERT INTO oms_order_point_usage(order_id,member_id,point_type,used_points,deducted_amount)"
                        + " VALUES(?,?,?,?,?)", orderId, memberId, pointType, points, amount);
        jdbcTemplate.update("INSERT INTO member_point_ledger"
                        + "(member_id,point_type,change_amount,balance_after,source_type,source_id)"
                        + " VALUES(?,?,?,?,'PURCHASE_DEDUCTION',?)",
                memberId, pointType, -points, account.getBalance(), orderId);
        if (walletType == PointsWalletService.TYPE_PRODUCT) {
            jdbcTemplate.update("UPDATE ums_member SET integration=? WHERE id=?", account.getBalance(), memberId);
        }
    }

    private void refundOrderPoints(OmsOrder order) {
        List<Map<String, Object>> usage = jdbcTemplate.queryForList(
                "SELECT point_type,used_points FROM oms_order_point_usage WHERE order_id=? FOR UPDATE", order.getId());
        for (Map<String, Object> row : usage) {
            String pointType = (String) row.get("point_type");
            int walletType = "PRODUCT".equals(pointType) ? PointsWalletService.TYPE_PRODUCT
                    : PointsWalletService.TYPE_REPURCHASE;
            long points = ((Number) row.get("used_points")).longValue();
            com.macro.mall.common.api.CommonResult<Void> changed = pointsWalletService.changePoints(
                    order.getMemberId(), walletType, "earn", points, Long.toString(order.getId()),
                    "order:points-refund:" + order.getId() + ":" + pointType, null, "未支付订单取消返还积分");
            if (changed.getCode() != 200) Asserts.fail("积分返还失败: " + changed.getMessage());
            com.macro.mall.portal.domain.UmsPointsAccount account =
                    pointsWalletService.getAccount(order.getMemberId(), walletType);
            if (account == null || account.getBalance() == null) Asserts.fail("积分返还后余额读取失败");
            jdbcTemplate.update("INSERT INTO member_point_ledger"
                            + "(member_id,point_type,change_amount,balance_after,source_type,source_id)"
                            + " VALUES(?,?,?,?,'REFUND',?)",
                    order.getMemberId(), pointType, points, account.getBalance(), order.getId());
            if (walletType == PointsWalletService.TYPE_PRODUCT) {
                jdbcTemplate.update("UPDATE ums_member SET integration=? WHERE id=?",
                        account.getBalance(), order.getMemberId());
            }
        }
        if (usage.isEmpty() && order.getUseIntegration() != null && order.getUseIntegration() > 0) {
            // Older orders predate the dedicated wallet debit ledger.
            UmsMember member = memberService.getById(order.getMemberId());
            memberService.updateIntegration(order.getMemberId(), member.getIntegration() + order.getUseIntegration());
        }
    }

    /**
     * 对优惠券优惠进行处理
     *
     * @param orderItemList       order_item列表
     * @param couponHistoryDetail 可用优惠券详情
     */
    private void handleCouponAmount(List<OmsOrderItem> orderItemList, SmsCouponHistoryDetail couponHistoryDetail) {
        SmsCoupon coupon = couponHistoryDetail.getCoupon();
        if (coupon.getUseType().equals(0)) {
            //全场通用
            calcPerCouponAmount(orderItemList, coupon);
        } else if (coupon.getUseType().equals(1)) {
            //指定分类
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 0);
            calcPerCouponAmount(couponOrderItemList, coupon);
        } else if (coupon.getUseType().equals(2)) {
            //指定商品
            List<OmsOrderItem> couponOrderItemList = getCouponOrderItemByRelation(couponHistoryDetail, orderItemList, 1);
            calcPerCouponAmount(couponOrderItemList, coupon);
        }
    }

    /**
     * 对每个下单商品进行优惠券金额分摊的计算
     *
     * @param orderItemList 可用优惠券的下单商品商品
     */
    private void calcPerCouponAmount(List<OmsOrderItem> orderItemList, SmsCoupon coupon) {
        BigDecimal totalAmount = calcTotalAmount(orderItemList);
        for (OmsOrderItem orderItem : orderItemList) {
            //(商品价格/可用商品总价)*优惠券面额
            BigDecimal couponAmount = orderItem.getProductPrice().divide(totalAmount, 3, RoundingMode.HALF_EVEN).multiply(coupon.getAmount());
            orderItem.setCouponAmount(couponAmount);
        }
    }

    /**
     * 获取与优惠券有关系的下单商品
     *
     * @param couponHistoryDetail 优惠券详情
     * @param orderItemList       下单商品
     * @param type                使用关系类型：0->相关分类；1->指定商品
     */
    private List<OmsOrderItem> getCouponOrderItemByRelation(SmsCouponHistoryDetail couponHistoryDetail, List<OmsOrderItem> orderItemList, int type) {
        List<OmsOrderItem> result = new ArrayList<>();
        if (type == 0) {
            List<Long> categoryIdList = new ArrayList<>();
            for (SmsCouponProductCategoryRelation productCategoryRelation : couponHistoryDetail.getCategoryRelationList()) {
                categoryIdList.add(productCategoryRelation.getProductCategoryId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (categoryIdList.contains(orderItem.getProductCategoryId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        } else if (type == 1) {
            List<Long> productIdList = new ArrayList<>();
            for (SmsCouponProductRelation productRelation : couponHistoryDetail.getProductRelationList()) {
                productIdList.add(productRelation.getProductId());
            }
            for (OmsOrderItem orderItem : orderItemList) {
                if (productIdList.contains(orderItem.getProductId())) {
                    result.add(orderItem);
                } else {
                    orderItem.setCouponAmount(new BigDecimal(0));
                }
            }
        }
        return result;
    }

    /**
     * 获取该用户可以使用的优惠券
     *
     * @param cartPromotionItemList 购物车优惠列表
     * @param couponId              使用优惠券id
     */
    private SmsCouponHistoryDetail getUseCoupon(List<CartPromotionItem> cartPromotionItemList, Long couponId) {
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        for (SmsCouponHistoryDetail couponHistoryDetail : couponHistoryDetailList) {
            if (couponHistoryDetail.getCoupon().getId().equals(couponId)) {
                return couponHistoryDetail;
            }
        }
        return null;
    }

    /**
     * 计算总金额
     */
    private BigDecimal calcTotalAmount(List<OmsOrderItem> orderItemList) {
        BigDecimal totalAmount = new BigDecimal("0");
        for (OmsOrderItem item : orderItemList) {
            totalAmount = totalAmount.add(item.getProductPrice().multiply(new BigDecimal(item.getProductQuantity())));
        }
        return totalAmount;
    }

    /**
     * 锁定下单商品的所有库存
     */
    private void lockStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            if (portalOrderDao.reserveSku(cartPromotionItem.getProductSkuId(), cartPromotionItem.getQuantity()) != 1) {
                Asserts.fail("库存不足，无法下单");
            }
        }
    }

    /**
     * 判断下单商品是否都有库存
     */
    private boolean hasStock(List<CartPromotionItem> cartPromotionItemList) {
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            if (cartPromotionItem.getRealStock()==null //判断真实库存是否为空
                    ||cartPromotionItem.getRealStock() <= 0 //判断真实库存是否小于0
                    || cartPromotionItem.getRealStock() < cartPromotionItem.getQuantity()) //判断真实库存是否小于下单的数量
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算购物车中商品的价格
     */
    private ConfirmOrderResult.CalcAmount calcCartAmount(List<CartPromotionItem> cartPromotionItemList) {
        ConfirmOrderResult.CalcAmount calcAmount = new ConfirmOrderResult.CalcAmount();
        calcAmount.setFreightAmount(new BigDecimal(0));
        BigDecimal totalAmount = new BigDecimal("0");
        BigDecimal promotionAmount = new BigDecimal("0");
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            totalAmount = totalAmount.add(cartPromotionItem.getPrice().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
            promotionAmount = promotionAmount.add(cartPromotionItem.getReduceAmount().multiply(new BigDecimal(cartPromotionItem.getQuantity())));
        }
        calcAmount.setTotalAmount(totalAmount);
        calcAmount.setPromotionAmount(promotionAmount);
        calcAmount.setPayAmount(totalAmount.subtract(promotionAmount));
        return calcAmount;
    }

}
