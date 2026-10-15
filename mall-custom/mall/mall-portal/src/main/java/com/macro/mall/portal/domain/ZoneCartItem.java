package com.macro.mall.portal.domain;

import com.macro.mall.model.OmsCartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 带专区标识的购物车项：记录商品是从哪个专区加入购物车的，购物车与结算按专区隔离。
 */
@Getter
@Setter
public class ZoneCartItem extends OmsCartItem {
    @Schema(title = "加入购物车时的专区：PICK/REBUY/CONSUME，空为未分配专区")
    private String zoneCode;
    @Schema(title = "专区名称")
    private String zoneLabel;
    @Schema(title = "专区结算方式")
    private String zoneRule;
}
