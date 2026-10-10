package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.UmsMemberIdentityLogMapper;
import com.macro.mall.portal.dao.UmsMemberIdentityMapper;
import com.macro.mall.portal.domain.UmsMemberIdentity;
import com.macro.mall.portal.domain.UmsMemberIdentityLog;
import com.macro.mall.portal.service.MemberIdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class MemberIdentityServiceImpl implements MemberIdentityService {

    @Autowired
    private UmsMemberIdentityMapper identityMapper;
    @Autowired
    private UmsMemberIdentityLogMapper identityLogMapper;

    @Override
    public UmsMemberIdentity getIdentity(Long memberId) {
        return identityMapper.selectByMember(memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> changeIdentity(Long memberId, int newType, Long operatorId, String reason, Long bizOrderId) {
        if (newType < 0 || newType > 2) {
            return CommonResult.failed("无效身份类型");
        }
        UmsMemberIdentity existing = identityMapper.selectByMember(memberId);
        Integer fromType = existing == null ? null : existing.getIdentityType();
        if (existing == null) {
            UmsMemberIdentity i = new UmsMemberIdentity();
            i.setMemberId(memberId);
            i.setIdentityType(newType);
            i.setStatus(1);
            i.setBecomeTime(new Date());
            i.setOperatorId(operatorId);
            i.setCreateTime(new Date());
            i.setUpdateTime(new Date());
            identityMapper.insert(i);
        } else {
            existing.setIdentityType(newType);
            existing.setStatus(1);
            existing.setBecomeTime(new Date());
            existing.setOperatorId(operatorId);
            existing.setUpdateTime(new Date());
            identityMapper.updateByMember(existing);
        }
        UmsMemberIdentityLog log = new UmsMemberIdentityLog();
        log.setMemberId(memberId);
        log.setFromType(fromType);
        log.setToType(newType);
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setBizOrderId(bizOrderId);
        log.setCreateTime(new Date());
        identityLogMapper.insert(log);
        return CommonResult.success(null);
    }
}
