package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsMemberIdentityLog;
import org.apache.ibatis.annotations.Insert;
import org.springframework.stereotype.Repository;

@Repository
public interface UmsMemberIdentityLogMapper {

    @Insert("INSERT INTO ums_member_identity_log(member_id,from_type,to_type,reason,operator_id,biz_order_id) " +
            "VALUES(#{memberId},#{fromType},#{toType},#{reason},#{operatorId},#{bizOrderId})")
    int insert(UmsMemberIdentityLog l);
}
