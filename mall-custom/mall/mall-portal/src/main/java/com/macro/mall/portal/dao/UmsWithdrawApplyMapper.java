package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsWithdrawApply;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UmsWithdrawApplyMapper {

    @Insert("INSERT INTO ums_withdraw_apply(member_id,idempotency_key,amount,fee_rate,fee_amount,actual_amount,status,simulate_flag) " +
            "VALUES(#{memberId},#{idempotencyKey},#{amount},#{feeRate},#{feeAmount},#{actualAmount},#{status},#{simulateFlag})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UmsWithdrawApply a);

    @Select("SELECT * FROM ums_withdraw_apply WHERE member_id=#{memberId} AND idempotency_key=#{key}")
    UmsWithdrawApply selectByRequest(@Param("memberId") Long memberId, @Param("key") String key);

    @Select("SELECT * FROM ums_withdraw_apply WHERE id=#{id}")
    UmsWithdrawApply selectById(@Param("id") Long id);

    @Select("SELECT * FROM ums_withdraw_apply WHERE member_id=#{memberId} ORDER BY id DESC")
    List<UmsWithdrawApply> selectByMember(@Param("memberId") Long memberId);

    @Update("UPDATE ums_withdraw_apply SET status=#{status},audit_id=#{auditId},audit_remark=#{auditRemark},audit_time=#{auditTime} " +
            "WHERE id=#{id}")
    int updateStatus(UmsWithdrawApply a);
}
