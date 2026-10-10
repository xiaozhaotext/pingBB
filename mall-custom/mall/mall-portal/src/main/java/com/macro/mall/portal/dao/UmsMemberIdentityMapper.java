package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsMemberIdentity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface UmsMemberIdentityMapper {

    @Insert("INSERT INTO ums_member_identity(member_id,identity_type,status,become_time,operator_id) " +
            "VALUES(#{memberId},#{identityType},#{status},#{becomeTime},#{operatorId})")
    int insert(UmsMemberIdentity i);

    @Select("SELECT * FROM ums_member_identity WHERE member_id=#{memberId} LIMIT 1")
    UmsMemberIdentity selectByMember(@Param("memberId") Long memberId);

    @Update("UPDATE ums_member_identity SET identity_type=#{identityType},status=#{status},become_time=#{becomeTime},operator_id=#{operatorId} " +
            "WHERE member_id=#{memberId}")
    int updateByMember(UmsMemberIdentity i);
}
