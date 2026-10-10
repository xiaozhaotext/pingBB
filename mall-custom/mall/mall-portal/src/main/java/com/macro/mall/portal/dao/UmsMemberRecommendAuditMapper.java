package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsMemberRecommendAudit;
import org.apache.ibatis.annotations.Insert;
import org.springframework.stereotype.Repository;

@Repository
public interface UmsMemberRecommendAuditMapper {

    @Insert("INSERT INTO ums_member_recommend_audit(member_id,inviter_id,invite_code,action,operator_id,detail) " +
            "VALUES(#{memberId},#{inviterId},#{inviteCode},#{action},#{operatorId},#{detail})")
    int insert(UmsMemberRecommendAudit a);
}
