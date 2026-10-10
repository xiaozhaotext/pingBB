package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.UmsMemberRecommend;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UmsMemberRecommendMapper {

    @Insert("INSERT INTO ums_member_recommend(member_id,inviter_id,invite_code,bind_order_id,bind_path) " +
            "VALUES(#{memberId},#{inviterId},#{inviteCode},#{bindOrderId},#{bindPath})")
    int insert(UmsMemberRecommend r);

    // 防重复绑定：返回已有记录表示已绑定
    @Select("SELECT * FROM ums_member_recommend WHERE member_id=#{memberId} LIMIT 1")
    UmsMemberRecommend selectByMember(@Param("memberId") Long memberId);

    @Select("SELECT * FROM ums_member_recommend WHERE inviter_id=#{inviterId}")
    List<UmsMemberRecommend> selectByInviter(@Param("inviterId") Long inviterId);

    // 沿推荐链向上查找某 memberId 是否在 inviter 的祖先链中（防循环）
    @Select("SELECT bind_path FROM ums_member_recommend WHERE member_id=#{memberId} LIMIT 1")
    String selectBindPathByMember(@Param("memberId") Long memberId);
}
