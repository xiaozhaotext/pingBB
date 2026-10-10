package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.DividendSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface DividendSnapshotMapper {

    @Insert("INSERT INTO dividend_snapshot(stat_date,member_id,contribution_value,pool_total,ratio,estimated_amount,simulate_flag,calc_batch) " +
            "VALUES(#{statDate},#{memberId},#{contributionValue},#{poolTotal},#{ratio},#{estimatedAmount},#{simulateFlag},#{calcBatch})")
    int insert(DividendSnapshot s);

    @Select("SELECT * FROM dividend_snapshot WHERE stat_date=#{statDate} AND member_id=#{memberId} AND calc_batch=#{calcBatch}")
    DividendSnapshot selectByDateMemberBatch(@Param("statDate") Date statDate, @Param("memberId") Long memberId, @Param("calcBatch") String calcBatch);

    @Select("SELECT * FROM dividend_snapshot WHERE stat_date=#{statDate} ORDER BY estimated_amount DESC")
    List<DividendSnapshot> selectByStatDate(@Param("statDate") Date statDate);
}
