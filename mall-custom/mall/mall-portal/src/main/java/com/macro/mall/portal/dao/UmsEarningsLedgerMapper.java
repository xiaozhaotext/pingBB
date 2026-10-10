package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsEarningsLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UmsEarningsLedgerMapper {

    @Insert("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,biz_id,idempotency_key,remark) " +
            "VALUES(#{memberId},#{changeType},#{amount},#{balanceAfter},#{bizId},#{idempotencyKey},#{remark})")
    int insert(UmsEarningsLedger l);

    @Select("SELECT * FROM ums_earnings_ledger WHERE idempotency_key=#{key}")
    UmsEarningsLedger selectByIdempotencyKey(@Param("key") String key);

    @Select("SELECT * FROM ums_earnings_ledger WHERE member_id=#{memberId} ORDER BY id DESC")
    List<UmsEarningsLedger> selectByMember(@Param("memberId") Long memberId);
}
