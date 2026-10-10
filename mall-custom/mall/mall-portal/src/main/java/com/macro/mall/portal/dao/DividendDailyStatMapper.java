package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.DividendDailyStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface DividendDailyStatMapper {

    @Insert("INSERT INTO dividend_daily_stat(stat_date,member_id,contribution_value,order_count,order_amount) " +
            "VALUES(#{statDate},#{memberId},#{contributionValue},#{orderCount},#{orderAmount})")
    int insert(DividendDailyStat s);

    @Delete("DELETE FROM dividend_daily_stat WHERE stat_date=#{statDate}")
    int deleteByStatDate(@Param("statDate") Date statDate);

    @Select("SELECT * FROM dividend_daily_stat WHERE stat_date=#{statDate}")
    List<DividendDailyStat> selectByStatDate(@Param("statDate") Date statDate);
}
