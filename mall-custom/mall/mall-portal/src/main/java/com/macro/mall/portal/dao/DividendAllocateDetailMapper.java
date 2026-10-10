package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.DividendAllocateDetail;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface DividendAllocateDetailMapper {

    @Insert("INSERT INTO dividend_allocate_detail(stat_date,member_id,ratio,estimated_amount,simulate_flag) " +
            "VALUES(#{statDate},#{memberId},#{ratio},#{estimatedAmount},#{simulateFlag})")
    int insert(DividendAllocateDetail d);

    @Select("SELECT * FROM dividend_allocate_detail WHERE stat_date=#{statDate} ORDER BY estimated_amount DESC")
    List<DividendAllocateDetail> selectByStatDate(@Param("statDate") Date statDate);
}
