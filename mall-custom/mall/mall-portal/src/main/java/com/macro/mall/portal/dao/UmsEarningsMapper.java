package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsEarnings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface UmsEarningsMapper {

    @Insert("INSERT INTO ums_earnings(member_id,total_earnings,available_earnings,frozen_earnings,withdrawn,version) " +
            "VALUES(#{memberId},#{totalEarnings},#{availableEarnings},#{frozenEarnings},#{withdrawn},#{version})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UmsEarnings e);

    @Select("SELECT * FROM ums_earnings WHERE member_id=#{memberId}")
    UmsEarnings selectByMember(@Param("memberId") Long memberId);

    @Update("UPDATE ums_earnings SET total_earnings=#{totalEarnings},available_earnings=#{availableEarnings}," +
            "frozen_earnings=#{frozenEarnings},withdrawn=#{withdrawn},version=version+1 " +
            "WHERE id=#{id} AND version=#{version}")
    int updateWithVersion(UmsEarnings e);
}
