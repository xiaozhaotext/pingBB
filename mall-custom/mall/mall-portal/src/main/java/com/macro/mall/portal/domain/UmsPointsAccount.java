package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 双积分账户（商品积分=1 / 复购积分=2）
 */
@Data
public class UmsPointsAccount implements Serializable {
    private Long id;
    private Long memberId;
    private Integer pointsType;
    private Long balance;
    private Long frozen;
    private Long available;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
