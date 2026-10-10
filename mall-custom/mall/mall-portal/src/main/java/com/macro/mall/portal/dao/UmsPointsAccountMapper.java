package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsPointsAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface UmsPointsAccountMapper {

    @Insert("INSERT INTO ums_points_account(member_id,points_type,balance,frozen,available,version) " +
            "VALUES(#{memberId},#{pointsType},#{balance},#{frozen},#{available},#{version})")
    int insert(UmsPointsAccount a);

    @Select("SELECT * FROM ums_points_account WHERE member_id=#{memberId} AND points_type=#{pointsType}")
    UmsPointsAccount selectByMemberAndType(@Param("memberId") Long memberId, @Param("pointsType") Integer pointsType);

    @Select("SELECT * FROM ums_points_account WHERE id=#{id}")
    UmsPointsAccount selectById(@Param("id") Long id);

    // 乐观锁更新：仅当 version 未变时成功，返回 0 表示并发冲突
    @Update("UPDATE ums_points_account SET balance=#{balance},frozen=#{frozen},available=#{available},version=version+1 " +
            "WHERE id=#{id} AND version=#{version}")
    int updateWithVersion(UmsPointsAccount a);
}
