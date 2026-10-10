package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.BizPartnerConfigMapper;
import com.macro.mall.portal.dao.BizPartnerOrderMapper;
import com.macro.mall.portal.domain.BizPartnerConfig;
import com.macro.mall.portal.domain.BizPartnerOrder;
import com.macro.mall.portal.service.MemberIdentityService;
import com.macro.mall.portal.service.PartnerService;
import com.macro.mall.portal.service.PointsWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

/**
 * 合伙人购买（模拟）：事务一致 + 幂等 + 身份升级 + 双积分赠送。
 * 所有金额均为模拟，不接入真实支付。
 */
@Service
public class PartnerServiceImpl implements PartnerService {

    @Autowired
    private BizPartnerConfigMapper configMapper;
    @Autowired
    private BizPartnerOrderMapper orderMapper;
    @Autowired
    private MemberIdentityService identityService;
    @Autowired
    private PointsWalletService pointsWalletService;

    @Override
    public BizPartnerConfig getConfig() {
        return configMapper.selectActive();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<BizPartnerOrder> createSimulateOrder(Long memberId, String bizId) {
        if (bizId == null || bizId.trim().isEmpty()) {
            return CommonResult.failed("bizId 不能为空");
        }
        if (orderMapper.selectByBizId(bizId) != null) {
            return CommonResult.failed("重复下单：bizId 已存在");
        }
        BizPartnerConfig cfg = configMapper.selectActive();
        if (cfg == null) {
            return CommonResult.failed("合伙人配置不存在");
        }
        BizPartnerOrder o = new BizPartnerOrder();
        o.setMemberId(memberId);
        o.setAmount(cfg.getPrice());
        o.setPayStatus(0);
        o.setSimulateFlag(1);
        o.setBizId(bizId);
        orderMapper.insert(o);
        return CommonResult.success(orderMapper.selectByBizId(bizId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> confirmSimulatePay(Long memberId, String bizId) {
        BizPartnerOrder o = orderMapper.selectByBizId(bizId);
        if (o == null) {
            return CommonResult.failed("订单不存在");
        }
        if (!memberId.equals(o.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他会员的模拟订单");
        }
        if (o.getPayStatus() != null && o.getPayStatus() == 1) {
            return CommonResult.failed("该订单已模拟支付，请勿重复操作");
        }
        BizPartnerConfig cfg = configMapper.selectActive();

        // 1) 标记订单为模拟支付成功
        o.setPayStatus(1);
        o.setPayTime(new Date());
        orderMapper.updateStatus(o);

        // 2) 身份升级为合伙人（写审计）
        CommonResult<Void> r0 = identityService.changeIdentity(memberId,
                MemberIdentityService.TYPE_PARTNER, memberId, "模拟购买合伙人权益", o.getId());
        if (r0.getCode() != 200) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, r0.getMessage());
        }

        // 3) 赠送双积分（幂等由 idempotencyKey 保证）
        String idem = "partner_grant_" + bizId;
        CommonResult<Void> r1 = pointsWalletService.changePoints(memberId,
                PointsWalletService.TYPE_PRODUCT, "partner_grant",
                cfg.getProductPoints().longValue(), bizId, idem + "_p", memberId, "合伙人赠送商品积分(模拟)");
        if (r1.getCode() != 200) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, r1.getMessage());
        }
        CommonResult<Void> r2 = pointsWalletService.changePoints(memberId,
                PointsWalletService.TYPE_REPURCHASE, "partner_grant",
                cfg.getRepurchasePoints().longValue(), bizId, idem + "_r", memberId, "合伙人赠送复购积分(模拟)");
        if (r2.getCode() != 200) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, r2.getMessage());
        }
        return CommonResult.success(null);
    }
}
