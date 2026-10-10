package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.BizCofounderConfigMapper;
import com.macro.mall.portal.dao.BizCofounderOrderMapper;
import com.macro.mall.portal.domain.BizCofounderConfig;
import com.macro.mall.portal.domain.BizCofounderOrder;
import com.macro.mall.portal.service.CofounderService;
import com.macro.mall.portal.service.MemberIdentityService;
import com.macro.mall.portal.service.PointsWalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

/**
 * 联创购买（模拟）：事务一致 + 幂等 + 身份升级 + 联创积分赠送。
 * 所有金额均为模拟，不接入真实支付。三份分红/分享奖励上限仅作规则草案，不自动发放。
 */
@Service
public class CofounderServiceImpl implements CofounderService {

    @Autowired
    private BizCofounderConfigMapper configMapper;
    @Autowired
    private BizCofounderOrderMapper orderMapper;
    @Autowired
    private MemberIdentityService identityService;
    @Autowired
    private PointsWalletService pointsWalletService;

    @Override
    public BizCofounderConfig getConfig() {
        return configMapper.selectActive();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<BizCofounderOrder> createSimulateOrder(Long memberId, String bizId) {
        if (bizId == null || bizId.trim().isEmpty()) {
            return CommonResult.failed("bizId 不能为空");
        }
        if (orderMapper.selectByBizId(bizId) != null) {
            return CommonResult.failed("重复下单：bizId 已存在");
        }
        BizCofounderConfig cfg = configMapper.selectActive();
        if (cfg == null) {
            return CommonResult.failed("联创配置不存在");
        }
        BizCofounderOrder o = new BizCofounderOrder();
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
        BizCofounderOrder o = orderMapper.selectByBizId(bizId);
        if (o == null) {
            return CommonResult.failed("订单不存在");
        }
        if (!memberId.equals(o.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能操作其他会员的模拟订单");
        }
        if (o.getPayStatus() != null && o.getPayStatus() == 1) {
            return CommonResult.failed("该订单已模拟支付，请勿重复操作");
        }
        BizCofounderConfig cfg = configMapper.selectActive();

        // 1) 标记订单为模拟支付成功
        o.setPayStatus(1);
        o.setPayTime(new Date());
        orderMapper.updateStatus(o);

        // 2) 身份升级为联创（写审计）
        CommonResult<Void> r0 = identityService.changeIdentity(memberId,
                MemberIdentityService.TYPE_COFOLLOWER, memberId, "模拟购买联创权益", o.getId());
        if (r0.getCode() != 200) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, r0.getMessage());
        }

        // 3) 赠送联创积分（计入复购积分钱包，幂等由 idempotencyKey 保证）
        CommonResult<Void> r1 = pointsWalletService.changePoints(memberId,
                PointsWalletService.TYPE_REPURCHASE, "cofounder_grant",
                cfg.getCofounderPoints().longValue(), bizId, "cofounder_grant_" + bizId, memberId, "联创赠送积分(模拟)");
        if (r1.getCode() != 200) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, r1.getMessage());
        }
        return CommonResult.success(null);
    }
}
