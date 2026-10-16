package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.dao.UmsInvitationCodeMapper;
import com.macro.mall.portal.dao.UmsMemberRecommendAuditMapper;
import com.macro.mall.portal.dao.UmsMemberRecommendMapper;
import com.macro.mall.portal.domain.UmsInvitationCode;
import com.macro.mall.portal.domain.UmsMemberRecommend;
import com.macro.mall.portal.domain.UmsMemberRecommendAudit;
import com.macro.mall.portal.service.InvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

@Service
public class InvitationServiceImpl implements InvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @org.springframework.beans.factory.annotation.Value("${mall.public-base-url:http://127.0.0.1:5174}")
    private String publicBaseUrl;

    private String invitationLink(String code) {
        java.net.URI base = java.net.URI.create(publicBaseUrl);
        if ((!"https".equals(base.getScheme()) && !"http".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalStateException("商城公开地址配置无效");
        }
        return publicBaseUrl.replaceAll("/+$", "") + "/#/pages/member/invitation?inviteCode=" + code;
    }

    @Autowired
    private UmsInvitationCodeMapper codeMapper;
    @Autowired
    private UmsMemberRecommendMapper recommendMapper;
    @Autowired
    private UmsMemberRecommendAuditMapper auditMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<String> ensureCode(Long memberId) {
        UmsInvitationCode existing = codeMapper.selectByMember(memberId);
        if (existing != null) {
            return CommonResult.success(existing.getCode());
        }
        String code = generateUniqueCode();
        UmsInvitationCode c = new UmsInvitationCode();
        c.setMemberId(memberId);
        c.setCode(code);
        c.setQrContent(invitationLink(code));
        c.setShortLink(invitationLink(code));
        c.setStatus(1);
        c.setCreateTime(new Date());
        codeMapper.insert(c);
        return CommonResult.success(code);
    }

    @Override
    public UmsInvitationCode getMyCode(Long memberId) {
        UmsInvitationCode detail = codeMapper.selectByMember(memberId);
        if (detail != null) { detail.setQrContent(invitationLink(detail.getCode())); detail.setShortLink(invitationLink(detail.getCode())); }
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<UmsMemberRecommend> bind(Long inviteeId, String code, Long operatorId) {
        if (!StringUtils.hasText(code)) {
            return CommonResult.failed("邀请码不能为空");
        }
        // 同一被邀请人并发提交时串行检查，避免两次请求都通过已绑定检查。
        jdbc.queryForObject("SELECT id FROM ums_member WHERE id=? FOR UPDATE", Long.class, inviteeId);
        if (recommendMapper.selectByMember(inviteeId) != null) {
            return CommonResult.failed("您已有推荐人，无法再次绑定");
        }
        UmsInvitationCode invCode = codeMapper.selectByCode(code.trim());
        if (invCode == null) {
            audit("bind", inviteeId, null, code, "reject", operatorId, "邀请码不存在");
            return CommonResult.failed("邀请码无效");
        }
        Long inviterId = invCode.getMemberId();
        // 防自改/自推荐
        if (inviterId.equals(inviteeId)) {
            audit("bind", inviteeId, inviterId, code, "self_blocked", operatorId, "不能绑定自己的邀请码");
            return CommonResult.failed("不能绑定自己的邀请码");
        }
        // 防循环：invitee 不能处于 inviter 的祖先链中
        if (isAncestor(inviteeId, inviterId)) {
            audit("bind", inviteeId, inviterId, code, "cycle_blocked", operatorId, "检测到循环推荐关系，已阻断");
            return CommonResult.failed("推荐关系存在循环，绑定被阻断");
        }
        // 构建绑定链
        UmsMemberRecommend parent = recommendMapper.selectByMember(inviterId);
        String parentPath = parent == null ? ("/" + inviterId) : parent.getBindPath();
        String bindPath = parentPath + "/" + inviteeId;

        UmsMemberRecommend r = new UmsMemberRecommend();
        r.setMemberId(inviteeId);
        r.setInviterId(inviterId);
        r.setInviteCode(code.trim());
        r.setBindPath(bindPath);
        r.setBindTime(new Date());
        r.setCreateTime(new Date());
        recommendMapper.insert(r);

        audit("bind", inviteeId, inviterId, code, "bind", operatorId, "推荐关系绑定成功, path=" + bindPath);
        return CommonResult.success(r);
    }

    @Override
    public UmsMemberRecommend getMyInviter(Long memberId) {
        return recommendMapper.selectByMember(memberId);
    }

    @Override
    public List<UmsMemberRecommend> listMyInvitees(Long memberId) {
        return recommendMapper.selectByInviter(memberId);
    }

    /** 判断 ancestorId 是否是 nodeId 的祖先（用于防循环） */
    private boolean isAncestor(Long ancestorId, Long nodeId) {
        Long cur = nodeId;
        int depth = 0;
        while (cur != null && depth < 50) {
            if (cur.equals(ancestorId)) {
                return true;
            }
            UmsMemberRecommend rec = recommendMapper.selectByMember(cur);
            if (rec == null) {
                return false;
            }
            cur = rec.getInviterId();
            depth++;
        }
        return false;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (codeMapper.selectByCode(candidate) == null) {
                return candidate;
            }
        }
        throw new RuntimeException("生成唯一邀请码失败");
    }

    private void audit(String action, Long memberId, Long inviterId, String code, String result, Long operatorId, String detail) {
        UmsMemberRecommendAudit a = new UmsMemberRecommendAudit();
        a.setMemberId(memberId);
        a.setInviterId(inviterId);
        a.setInviteCode(code);
        a.setAction(result);
        a.setOperatorId(operatorId);
        a.setDetail(detail);
        a.setCreateTime(new Date());
        auditMapper.insert(a);
    }
}
