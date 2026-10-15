package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 确认页结算预览参数：切换优惠券或支付方式后按专区重新计算最终金额。
 */
@Getter
@Setter
public class SettlementPreviewParam {
    @Schema(title = "被选中的购物车商品ID")
    private List<Long> cartIds;
    @Schema(title = "优惠券ID，为空表示不使用优惠券")
    private Long couponId;
    @Schema(title = "自选区是否使用余额（充值本金）支付")
    private Boolean useBalance;
    @Schema(title = "复购区是否使用「复购积分 + 现金」模式，不传表示全现金")
    private Boolean useRepurchasePoints;
}
