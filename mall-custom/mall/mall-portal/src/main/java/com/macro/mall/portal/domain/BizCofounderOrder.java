package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 联创模拟购买订单（模拟支付后身份升级为联创）
 */
@Data
public class BizCofounderOrder implements Serializable {
    private Long id;
    private Long memberId;
    private BigDecimal amount;
    private Integer payStatus;   // 0->待支付; 1->模拟支付成功; 2->已取消
    private Integer simulateFlag;
    private String bizId;        // 幂等键
    private Date createTime;
    private Date payTime;
}
