package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车/订单按专区隔离结算的结果。
 *
 * <p>每个专区只允许使用本专区的结算方式，最终金额 = 各专区现金部分之和。
 */
@Getter
@Setter
public class ZoneSettlement {
    @Schema(title = "各专区结算明细")
    private List<Plan> plans = new ArrayList<>();
    @Schema(title = "商品总金额")
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Schema(title = "活动优惠合计")
    private BigDecimal promotionAmount = BigDecimal.ZERO;
    @Schema(title = "优惠券优惠合计")
    private BigDecimal couponAmount = BigDecimal.ZERO;
    @Schema(title = "消费区商品积分抵扣金额")
    private BigDecimal productPointAmount = BigDecimal.ZERO;
    @Schema(title = "复购区复购积分抵扣金额")
    private BigDecimal repurchasePointAmount = BigDecimal.ZERO;
    @Schema(title = "自选区余额（充值本金）抵扣金额")
    private BigDecimal balanceAmount = BigDecimal.ZERO;
    @Schema(title = "最终现金应付金额")
    private BigDecimal cashAmount = BigDecimal.ZERO;

    /** 每个专区的结算明细。 */
    @Getter
    @Setter
    public static class Plan {
        @Schema(title = "专区标识：PICK/REBUY/CONSUME")
        private String zoneCode;
        @Schema(title = "专区名称")
        private String zoneLabel;
        @Schema(title = "结算方式")
        private String zoneRule;
        @Schema(title = "该专区商品金额")
        private BigDecimal grossAmount = BigDecimal.ZERO;
        @Schema(title = "该专区活动优惠")
        private BigDecimal promotionAmount = BigDecimal.ZERO;
        @Schema(title = "该专区优惠券优惠")
        private BigDecimal couponAmount = BigDecimal.ZERO;
        @Schema(title = "该专区应收金额（扣券后）")
        private BigDecimal netAmount = BigDecimal.ZERO;
        @Schema(title = "本专区使用的商品积分数（消费区）")
        private long productPoints;
        @Schema(title = "本专区商品积分抵扣金额（消费区）")
        private BigDecimal productPointAmount = BigDecimal.ZERO;
        @Schema(title = "本专区使用的复购积分数（复购区）")
        private long repurchasePoints;
        @Schema(title = "本专区复购积分抵扣金额（复购区）")
        private BigDecimal repurchasePointAmount = BigDecimal.ZERO;
        @Schema(title = "本专区使用的余额（自选区）")
        private BigDecimal balanceAmount = BigDecimal.ZERO;
        @Schema(title = "本专区现金应付金额")
        private BigDecimal cashAmount = BigDecimal.ZERO;
        @Schema(title = "本专区可用的结算方式是否已满足；false 时不能下单")
        private boolean payable = true;
        @Schema(title = "不可下单原因")
        private String blockedReason;
        @Schema(title = "商品件数")
        private int quantity;
    }
}
