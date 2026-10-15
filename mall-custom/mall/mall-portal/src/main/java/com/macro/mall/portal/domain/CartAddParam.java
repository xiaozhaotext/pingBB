package com.macro.mall.portal.domain;

import com.macro.mall.model.OmsCartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 加入购物车参数：在原购物车字段上增加专区，便于区分商品从哪个专区加入。
 */
@Getter
@Setter
public class CartAddParam extends OmsCartItem {
    @Schema(title = "加入购物车时所在专区：PICK/REBUY/CONSUME，为空则按商品归属推断")
    private String zoneCode;
}
