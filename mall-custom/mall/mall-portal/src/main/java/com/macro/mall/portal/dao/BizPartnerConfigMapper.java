package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.BizPartnerConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Repository
public interface BizPartnerConfigMapper {

    @Select("SELECT * FROM biz_partner_config WHERE enabled=1 ORDER BY id DESC LIMIT 1")
    BizPartnerConfig selectActive();

    @Select("SELECT * FROM biz_partner_config WHERE id=#{id}")
    BizPartnerConfig selectById(@Param("id") Long id);
}
