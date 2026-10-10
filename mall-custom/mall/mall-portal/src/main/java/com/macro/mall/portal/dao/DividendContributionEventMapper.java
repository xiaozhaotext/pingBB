package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.DividendContributionEvent;
import com.macro.mall.portal.domain.DividendDailyStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Repository
public interface DividendContributionEventMapper {

    @Insert("INSERT INTO dividend_contribution_event(member_id,event_type,biz_id,contribution_value,verify_status,stat_date) " +
            "VALUES(#{memberId},#{eventType},#{bizId},#{contributionValue},#{verifyStatus},#{statDate})")
    int insert(DividendContributionEvent e);

    @Select("SELECT * FROM dividend_contribution_event WHERE biz_id=#{bizId} AND event_type=#{eventType}")
    DividendContributionEvent selectByBizEvent(@Param("bizId") String bizId, @Param("eventType") String eventType);

    @Select("SELECT * FROM dividend_contribution_event WHERE stat_date=#{statDate}")
    List<DividendContributionEvent> selectByStatDate(@Param("statDate") Date statDate);

    @Select("SELECT COALESCE(SUM(contribution_value),0) FROM dividend_contribution_event WHERE stat_date=#{statDate}")
    BigDecimal sumTotal(@Param("statDate") Date statDate);

    @Select("SELECT member_id AS memberId, COALESCE(SUM(contribution_value),0) AS contributionValue " +
            "FROM dividend_contribution_event WHERE stat_date=#{statDate} GROUP BY member_id")
    List<DividendDailyStat> sumByMember(@Param("statDate") Date statDate);
}
