package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 每日分红池-贡献事件（可核验真实业务行为：订单完成/退款/取消）
 */
@Data
public class DividendContributionEvent implements Serializable {
    private Long id;
    private Long memberId;
    private String eventType;        // order_complete/order_refund/order_cancel
    private String bizId;            // 业务单号(订单ID)
    private BigDecimal contributionValue; // 贡献值(正=产生, 负=冲回)
    private Integer verifyStatus;    // 1->已核验真实业务; 0->待核验
    private Date statDate;
    private Date createTime;
}
