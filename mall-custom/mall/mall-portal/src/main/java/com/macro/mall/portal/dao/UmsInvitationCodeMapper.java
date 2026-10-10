package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsInvitationCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Repository
public interface UmsInvitationCodeMapper {

    @Insert("INSERT INTO ums_invitation_code(member_id,code,qr_content,short_link,status) " +
            "VALUES(#{memberId},#{code},#{qrContent},#{shortLink},#{status})")
    int insert(UmsInvitationCode c);

    @Select("SELECT * FROM ums_invitation_code WHERE member_id=#{memberId} LIMIT 1")
    UmsInvitationCode selectByMember(@Param("memberId") Long memberId);

    @Select("SELECT * FROM ums_invitation_code WHERE code=#{code} LIMIT 1")
    UmsInvitationCode selectByCode(@Param("code") String code);
}
