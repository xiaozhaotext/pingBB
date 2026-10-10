package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsPointsLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UmsPointsLedgerMapper {

    @Insert("INSERT INTO ums_points_ledger(member_id,points_type,change_type,amount,balance_after,biz_id,idempotency_key,operator_id,remark) " +
            "VALUES(#{memberId},#{pointsType},#{changeType},#{amount},#{balanceAfter},#{bizId},#{idempotencyKey},#{operatorId},#{remark})")
    int insert(UmsPointsLedger l);

    // 幂等：同一 idempotency_key 只应有一条
    @Select("SELECT * FROM ums_points_ledger WHERE idempotency_key=#{key} LIMIT 1")
    UmsPointsLedger selectByIdempotencyKey(@Param("key") String key);

    @Select("SELECT * FROM ums_points_ledger WHERE member_id=#{memberId} ORDER BY create_time DESC")
    List<UmsPointsLedger> selectByMember(@Param("memberId") Long memberId);
}
