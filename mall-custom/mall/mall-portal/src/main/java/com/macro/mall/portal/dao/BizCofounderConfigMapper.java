package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.BizCofounderConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Repository
public interface BizCofounderConfigMapper {

    @Select("SELECT * FROM biz_cofounder_config WHERE enabled=1 ORDER BY id DESC LIMIT 1")
    BizCofounderConfig selectActive();

    @Select("SELECT * FROM biz_cofounder_config WHERE id=#{id}")
    BizCofounderConfig selectById(@Param("id") Long id);
}
