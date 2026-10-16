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
    /** 非现金渠道支付方式：余额或商品积分已全额抵扣，订单没有现金部分。 */
    private static final int PAY_TYPE_BALANCE_POINTS = 4;

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
    @Autowired
    private ZoneSettlementService zoneSettlementService;
    @Autowired
    private RechargeService rechargeService;
    @Autowired
    private TriMembershipOrderService triMembershipOrders;

    @Override
    public ConfirmOrderResult generateConfirmOrder(List<Long> cartIds) {
        return buildConfirmOrder(cartIds, null, false, false);
    }

    @Override
    public ConfirmOrderResult settlementPreview(SettlementPreviewParam param) {
        SettlementPreviewParam request = param == null ? new SettlementPreviewParam() : param;
        return buildConfirmOrder(request.getCartIds(), request.getCouponId(),
                Boolean.TRUE.equals(request.getUseBalance()),
                Boolean.TRUE.equals(request.getUseRepurchasePoints()));
    }

    /**
     * 生成确认单：按专区隔离计算每个专区的结算方式与最终金额。
     *
     * <p>自选区只能现金或余额（充值本金），复购区只能复购积分加现金，消费区只能用商品积分，
     * 专区之间不允许串用；最终现金应付 = 各专区现金部分之和。
     *
     * @param useBalance          自选区是否用余额（充值本金）支付
     * @param useRepurchasePoints 复购区是否用「复购积分 + 现金」模式，false 表示复购区全现金
     */
    private ConfirmOrderResult buildConfirmOrder(List<Long> cartIds, Long couponId,
                                                 boolean useBalance, boolean useRepurchasePoints) {
        ConfirmOrderResult result = new ConfirmOrderResult();
        //获取购物车信息（含加入购物车时所在专区）
        UmsMember currentMember = memberService.getCurrentMember();
        List<CartPromotionItem> cartPromotionItemList = cartItemService.listPromotion(currentMember.getId(), cartIds);
        result.setCartPromotionItemList(cartPromotionItemList);
        //获取用户收货地址列表
        result.setMemberReceiveAddressList(memberReceiveAddressService.list());
        //获取用户可用优惠券列表
        List<SmsCouponHistoryDetail> couponHistoryDetailList = memberCouponService.listCart(cartPromotionItemList, 1);
        result.setCouponHistoryDetailList(couponHistoryDetailList);
        //获取用户可用余额：商品积分、复购积分、余额（充值本金）
        ZoneSettlementService.Wallets wallets = wallets(currentMember.getId());
        result.setMemberIntegration((int) Math.min(wallets.productPoints(), Integer.MAX_VALUE));
        result.setProductPointsBalance(wallets.productPoints());
        result.setRepurchasePointsBalance(wallets.repurchasePoints());
        result.setRechargeBalance(wallets.balance());
        result.setProductPointYuanPerPoint(zoneSettlementService.productPointYuanPerPoint());
        result.setProductPointMaxRatio(zoneSettlementService.productPointMaxRatio());
        result.setRebuyPointsRatio(zoneSettlementService.rebuyPointsRatio());
        //获取积分使用规则
        result.setIntegrationConsumeSetting(integrationConsumeSettingMapper.selectByPrimaryKey(1L));
        //优惠券只在本专区商品内分摊，不跨专区抵扣
        List<OmsOrderItem> couponItemList = draftOrderItemsWithCoupon(
                cartPromotionItemList, couponHistoryDetailList, couponId);
        ZoneSettlement settlement = zoneSettlementService.settle(
                settlementLines(cartPromotionItemList, couponItemList), useRepurchasePoints, useBalance, wallets);
        result.setZoneSettlement(settlement);
        result.setRebuyEligible(!settlement.getPlans().isEmpty() && settlement.getPlans().stream()
                .allMatch(plan -> ZoneRules.REBUY.equals(plan.getZoneCode())));
        //计算总金额、活动优惠、优惠券、最终应付金额
        ConfirmOrderResult.CalcAmount calcAmount = new ConfirmOrderResult.CalcAmount();
        calcAmount.setTotalAmount(settlement.getTotalAmount());
        calcAmount.setFreightAmount(BigDecimal.ZERO);
        calcAmount.setPromotionAmount(settlement.getPromotionAmount());
        calcAmount.setPayAmount(settlement.getCashAmount());
        result.setCalcAmount(calcAmount);
        return result;
    }

    /** 会员三个可用账户：商品积分、复购积分、余额（充值本金，元）。 */
    private ZoneSettlementService.Wallets wallets(Long memberId) {
        return new ZoneSettlementService.Wallets(
                availablePoints(memberId, PointsWalletService.TYPE_PRODUCT),
                availablePoints(memberId, PointsWalletService.TYPE_REPURCHASE),
                BigDecimal.valueOf(rechargeService.balanceCents(memberId), 2));
    }

    private long availablePoints(Long memberId, int walletType) {
        com.macro.mall.portal.domain.UmsPointsAccount account = pointsWalletService.getAccount(memberId, walletType);
        return account == null || account.getAvailable() == null ? 0 : account.getAvailable();
    }

    /** 生成用于优惠券分摊的临时订单行：专区与购物车保持一致。 */
    private List<OmsOrderItem> draftOrderItemsWithCoupon(List<CartPromotionItem> cartPromotionItemList,
                                                        List<SmsCouponHistoryDetail> couponHistoryDetailList,
                                                        Long couponId) {
        List<OmsOrderItem> items = new ArrayList<>();
        for (CartPromotionItem cartPromotionItem : cartPromotionItemList) {
            items.add(toOrderItem(cartPromotionItem));
        }
        if (couponId != null && couponHistoryDetailList != null) {
            for (SmsCouponHistoryDetail detail : couponHistoryDetailList) {
                if (detail.getCoupon() != null && couponId.equals(detail.getCoupon().getId())) {
                    handleCouponAmount(items, detail);
                    break;
                }
            }
        }
        return items;
    }

    /** 购物车促销项转订单行，并记录加入购物车时所在专区（专区规则隔离的依据）。 */
    private OmsOrderItem toOrderItem(CartPromotionItem cartPromotionItem) {
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
        orderItem.setPromotionAmount(zeroIfNull(cartPromotionItem.getReduceAmount()));
        orderItem.setPromotionName(cartPromotionItem.getPromotionMessage());
        orderItem.setGiftIntegration(ZoneRules.PICK.equals(ZoneRules.normalize(cartPromotionItem.getZoneCode()))
                ? cartPromotionItem.getIntegration() : 0);
        orderItem.setGiftGrowth(cartPromotionItem.getGrowth());
        orderItem.setCouponAmount(BigDecimal.ZERO);
        orderItem.setIntegrationAmount(BigDecimal.ZERO);
        orderItem.setZoneCode(ZoneRules.normalize(cartPromotionItem.getZoneCode()));
        return orderItem;
    }

    /** 把购物车促销项与优惠券结果合并成按专区结算的输入行。 */
    private void assertMembershipOffers(List<Long> productIds) {
        for (Long id : productIds) {
            Integer offer = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_config WHERE config_key IN ('membership.partner.product_id','membership.cofounder.product_id') AND config_value=?", Integer.class, id.toString());
            if (offer != null && offer > 0) Asserts.fail("旅游权益商品请通过商品详情的权益购买入口下单，以确保双积分和会员权益正确发放");
        }
    }

    private List<ZoneSettlementService.Line> settlementLines(List<CartPromotionItem> cartPromotionItemList,
                                                             List<OmsOrderItem> couponItemList) {
        assertMembershipOffers(cartPromotionItemList.stream().map(CartPromotionItem::getProductId).collect(Collectors.toList()));
        List<ZoneSettlementService.Line> lines = new ArrayList<>();
        for (int index = 0; index < cartPromotionItemList.size(); index++) {
            CartPromotionItem cartPromotionItem = cartPromotionItemList.get(index);
            BigDecimal couponAmount = couponItemList == null ? BigDecimal.ZERO
                    : zeroIfNull(couponItemList.get(index).getCouponAmount());
            lines.add(new ZoneSettlementService.Line(
                    ZoneRules.settlementZone(cartPromotionItem.getZoneCode()),
                    zeroIfNull(cartPromotionItem.getPrice()),
                    cartPromotionItem.getQuantity() == null ? 0 : cartPromotionItem.getQuantity(),
                    zeroIfNull(cartPromotionItem.getReduceAmount()),
                    couponAmount));
        }
        return lines;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
            //生成下单商品信息，并保留加入购物车时所在专区
            orderItemList.add(toOrderItem(cartPromotionItem));
        }
        //判断购物车中商品是否都有库存
        if (!hasStock(cartPromotionItemList)) {
            Asserts.fail("库存不足，无法下单");
        }
        //判断使用使用了优惠券
        if (orderParam.getCouponId() == null) {
            //不用优惠券
            for (OmsOrderItem orderItem : orderItemList) {
                orderItem.setCouponAmount(BigDecimal.ZERO);
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
        //按专区隔离结算：自选区现金/余额，复购区复购积分+现金，消费区商品积分，专区之间不能串用
        boolean useBalance = Boolean.TRUE.equals(orderParam.getUseBalance());
        boolean useRepurchasePoints = orderParam.getUseRepurchasePoints() != null
                && orderParam.getUseRepurchasePoints() > 0;
        ZoneSettlementService.Wallets wallets = wallets(currentMember.getId());
        ZoneSettlement settlement = zoneSettlementService.settle(
                settlementLines(cartPromotionItemList, orderItemList), useRepurchasePoints, useBalance, wallets);
        for (ZoneSettlement.Plan plan : settlement.getPlans()) {
            if (!plan.isPayable()) {
                Asserts.fail(plan.getZoneLabel() + "：" + plan.getBlockedReason());
            }
        }
        //把各专区抵扣金额分摊回本专区订单行，专区之间不互相抵扣
        allocateZoneDeduction(orderItemList, settlement);
        //计算order_item的实付金额
        handleRealAmount(orderItemList);
        //进行库存锁定
        lockStock(cartPromotionItemList);
        //根据商品合计、运费、活动优惠、优惠券、积分计算应付金额
        OmsOrder order = new OmsOrder();
        order.setDiscountAmount(new BigDecimal(0));
        order.setTotalAmount(settlement.getTotalAmount());
        order.setFreightAmount(new BigDecimal(0));
        order.setPromotionAmount(settlement.getPromotionAmount());
        order.setPromotionInfo(getOrderPromotionInfo(orderItemList));
        if (orderParam.getCouponId() == null) {
            order.setCouponAmount(new BigDecimal(0));
        } else {
            order.setCouponId(orderParam.getCouponId());
            order.setCouponAmount(settlement.getCouponAmount());
        }
        //消费区商品积分与复购区复购积分一起记入积分抵扣金额；余额（充值本金）单独记录，最终以专区结算为准
        long productPointsUsed = pointsUsed(settlement, ZoneRules.CONSUME);
        long repurchasePointsUsed = pointsUsed(settlement, ZoneRules.REBUY);
        order.setUseIntegration((int) Math.min(productPointsUsed, Integer.MAX_VALUE));
        order.setIntegrationAmount(settlement.getProductPointAmount().add(settlement.getRepurchasePointAmount()));
        order.setBalanceAmount(settlement.getBalanceAmount());
        order.setPayAmount(settlement.getCashAmount());
        if (order.getPayAmount().signum() < 0) Asserts.fail("订单应付金额不能小于 0");
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
        if (productPointsUsed > 0) {
            consumeOrderPoints(order.getId(), currentMember.getId(), PointsWalletService.TYPE_PRODUCT,
                    "PRODUCT", productPointsUsed, settlement.getProductPointAmount());
        }
        if (repurchasePointsUsed > 0) {
            consumeOrderPoints(order.getId(), currentMember.getId(), PointsWalletService.TYPE_REPURCHASE,
                    "REPURCHASE", repurchasePointsUsed, settlement.getRepurchasePointAmount());
        }
        //自选区使用余额（充值本金）支付：与现金二选一，同一事务内扣减并写流水
        if (settlement.getBalanceAmount().signum() > 0) {
            rechargeService.spendBalance(currentMember.getId(),
                    settlement.getBalanceAmount().movePointRight(2).longValueExact(),
                    Long.toString(order.getId()), "order:balance:" + order.getId(), "自选区订单余额支付");
        }
        for (OmsOrderItem orderItem : orderItemList) {
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
        }
        orderItemDao.insertList(orderItemList);
        if (triMembershipOrders != null) triMembershipOrders.snapshot(order, orderItemList);
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
        boolean cashFree = order.getPayAmount() != null && order.getPayAmount().signum() == 0;
        if (cashFree) {
            // 余额或商品积分已全额抵扣，订单没有现金部分，不需要再走现金渠道
            settleWithoutCash(order.getId(), productPointsUsed + repurchasePointsUsed, order.getPayAmount());
        } else {
            //发送延迟消息取消订单
            sendDelayMessageCancelOrder(order.getId());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItemList", orderItemList);
        result.put("paidImmediately", cashFree);
        return result;
    }

    @Override
    public Integer paySuccess(Long orderId, Integer payType) {
        if (orderId == null || payType == null
                || (payType != 1 && payType != 2 && payType != 3 && payType != PAY_TYPE_BALANCE_POINTS)) {
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
        boolean triMembership = triMembershipOrders != null && triMembershipOrders.fulfill(orderId);
        boolean newRules = triMembershipOrders != null && triMembershipOrders.enabled();
        if (!newRules) shareOrderBonusService.postForOrder(orderId, orderDetail.getMemberId(), orderDetail.getPayAmount());
        // 购买赠送产品积分：按订单 giftIntegration 入账到买家产品积分钱包
        if (!triMembership && (!newRules || triMembershipOrders.isMember(orderDetail.getMemberId())))
            grantOrderGiftPoints(orderId, orderDetail);
        return orderDetail.getOrderItemList().size();
    }

    /**
     * 订单支付成功后，给买家发放商品赠送的产品积分。
     * 幂等：用 order:gift:{orderId} 做幂等键，重复调用不重复发放。
     */
    private void grantOrderGiftPoints(Long orderId, com.macro.mall.portal.domain.OmsOrderDetail orderDetail) {
        int giftPoints = calcGifIntegration(orderDetail.getOrderItemList());
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
            //自选区余额（充值本金）抵扣部分原路退回
            refundBalanceToMember(cancelOrder);
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
        for (OmsOrderItem item : orderItemList) {
            // 按订单行记录的购买专区判定，禁止复购/消费/未分区商品赠积分。
            // 同一个商品在不同专区购买也必须分别处理，旧待支付订单同样受此限制。
            if (!ZoneRules.PICK.equals(item.getZoneCode())) continue;
            int gift = item.getGiftIntegration() == null ? 0 : item.getGiftIntegration();
            int quantity = item.getProductQuantity() == null ? 0 : item.getProductQuantity();
            if (gift > 0 && quantity > 0) sum = Math.addExact(sum, Math.multiplyExact(gift, quantity));
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
     * 把各专区结算出的抵扣金额分摊到本专区的订单行。
     *
     * <p>只有同一专区的商品参与分摊，专区之间不互相抵扣；消费区商品积分与复购区复购积分
     * 都记入 order_item.integration_amount，最终金额以专区结算结果为准。
     */
    private void allocateZoneDeduction(List<OmsOrderItem> orderItemList, ZoneSettlement settlement) {
        for (ZoneSettlement.Plan plan : settlement.getPlans()) {
            BigDecimal deduction = plan.getProductPointAmount().add(plan.getRepurchasePointAmount());
            if (deduction.signum() <= 0) {
                continue;
            }
            List<OmsOrderItem> zoneItems = orderItemList.stream()
                    .filter(item -> plan.getZoneCode().equals(ZoneRules.settlementZone(item.getZoneCode())))
                    .collect(Collectors.toList());
            allocateDeduction(zoneItems, deduction, plan.getZoneLabel());
        }
    }

    /** 按商品金额占比把专区抵扣金额分摊到订单行，最后一件吸收尾差，保证分摊合计等于专区抵扣金额。 */
    private void allocateDeduction(List<OmsOrderItem> items, BigDecimal deduction, String zoneLabel) {
        BigDecimal netTotal = BigDecimal.ZERO;
        for (OmsOrderItem item : items) {
            netTotal = netTotal.add(unitNetAmount(item).multiply(BigDecimal.valueOf(item.getProductQuantity())));
        }
        if (netTotal.signum() <= 0) {
            Asserts.fail(zoneLabel + "订单应付金额无效");
        }
        BigDecimal remaining = deduction;
        for (int index = 0; index < items.size(); index++) {
            OmsOrderItem item = items.get(index);
            BigDecimal unitNet = unitNetAmount(item);
            BigDecimal lineNet = unitNet.multiply(BigDecimal.valueOf(item.getProductQuantity()));
            BigDecimal lineDeduction = index == items.size() - 1 ? remaining
                    : deduction.multiply(lineNet).divide(netTotal, 2, RoundingMode.DOWN);
            try {
                BigDecimal perUnit = lineDeduction.divide(BigDecimal.valueOf(item.getProductQuantity()),
                        2, RoundingMode.UNNECESSARY);
                if (perUnit.signum() < 0 || perUnit.compareTo(unitNet) > 0) {
                    Asserts.fail(zoneLabel + "积分分摊金额无效");
                }
                item.setIntegrationAmount(perUnit);
            } catch (ArithmeticException ex) {
                Asserts.fail(zoneLabel + "当前多件商品的积分金额无法按分分摊，请调整数量或分开下单");
            }
            remaining = remaining.subtract(lineDeduction);
        }
    }

    /** 订单行「每件」的应收金额：原价 - 活动优惠 - 优惠券。 */
    private BigDecimal unitNetAmount(OmsOrderItem item) {
        return zeroIfNull(item.getProductPrice()).subtract(zeroIfNull(item.getPromotionAmount()))
                .subtract(zeroIfNull(item.getCouponAmount()));
    }

    /** 某专区实际使用的积分数：消费区为商品积分，复购区为复购积分。 */
    private long pointsUsed(ZoneSettlement settlement, String zoneCode) {
        long points = 0;
        for (ZoneSettlement.Plan plan : settlement.getPlans()) {
            if (plan.getZoneCode().equals(zoneCode)) {
                points += ZoneRules.CONSUME.equals(zoneCode) ? plan.getProductPoints() : plan.getRepurchasePoints();
            }
        }
        return points;
    }

    /**
     * 订单最终应付现金为 0（余额或商品积分已全额抵扣）时，直接按非现金渠道确认支付，
     * 避免订单停留在待付款、无法通过现金渠道支付。
     */
    private void settleWithoutCash(Long orderId, long pointsUsed, BigDecimal cashAmount) {
        String channel = pointsUsed > 0 ? "POINTS" : "BALANCE";
        jdbcTemplate.update("INSERT INTO oms_payment_confirmation"
                        + "(order_id,channel_code,channel_trade_no,paid_amount,currency_code) VALUES(?,?,?,?,'CNY')",
                orderId, channel, channel + "-" + orderId, cashAmount);
        paySuccess(orderId, PAY_TYPE_BALANCE_POINTS);
    }

    /** 待付款订单取消时把余额（充值本金）原路退回，幂等键绑定订单。 */
    private void refundBalanceToMember(OmsOrder order) {
        BigDecimal balance = zeroIfNull(order.getBalanceAmount());
        if (balance.signum() <= 0) {
            return;
        }
        rechargeService.refundBalance(order.getMemberId(), balance.movePointRight(2).longValueExact(),
                Long.toString(order.getId()), "order:balance-refund:" + order.getId(), "未支付订单取消退回余额");
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

}
