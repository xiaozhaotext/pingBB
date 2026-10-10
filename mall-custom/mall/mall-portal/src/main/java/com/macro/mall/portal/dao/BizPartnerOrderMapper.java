package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.BizPartnerOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface BizPartnerOrderMapper {

    @Insert("INSERT INTO biz_partner_order(member_id,amount,pay_status,simulate_flag,biz_id) " +
            "VALUES(#{memberId},#{amount},#{payStatus},#{simulateFlag},#{bizId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BizPartnerOrder o);

    @Select("SELECT * FROM biz_partner_order WHERE biz_id=#{bizId}")
    BizPartnerOrder selectByBizId(@Param("bizId") String bizId);

    @Update("UPDATE biz_partner_order SET pay_status=#{payStatus},pay_time=#{payTime} WHERE id=#{id}")
    int updateStatus(BizPartnerOrder o);
}
