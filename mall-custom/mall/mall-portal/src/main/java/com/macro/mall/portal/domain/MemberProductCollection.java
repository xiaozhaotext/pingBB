package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 会员商品收藏（MySQL 存储，字段沿用原 Mongo 文档结构）
 * Created by macro on 2018/8/2.
 */
@Getter
@Setter
public class MemberProductCollection {
    /** MySQL 自增主键，对外仍按字符串返回，保持原接口结构 */
    private String id;
    private Long memberId;
    private String memberNickname;
    private String memberIcon;
    private Long productId;
    private String productName;
    private String productPic;
    private String productSubTitle;
    private String productPrice;
    private Date createTime;
}
