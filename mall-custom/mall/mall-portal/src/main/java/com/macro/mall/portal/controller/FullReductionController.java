package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.portal.domain.FullReductionOffer;
import com.macro.mall.portal.domain.ZoneCartItem;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.OmsPromotionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order/fullReduction")
public class FullReductionController {
    private final OmsPortalOrderService orders;
    private final OmsPromotionService promotions;
    private final JdbcTemplate jdbc;

    public FullReductionController(OmsPortalOrderService orders, OmsPromotionService promotions, JdbcTemplate jdbc) {
        this.orders = orders;
        this.promotions = promotions;
        this.jdbc = jdbc;
    }

    /** Read-only suggestions. An existing order keeps its original amount and promotion snapshot. */
    @GetMapping("/{orderId}")
    public CommonResult<List<FullReductionOffer>> offers(@PathVariable Long orderId) {
        var order = orders.detail(orderId); // Existing ownership verification must precede all item reads.
        if (!Integer.valueOf(0).equals(order.getStatus())) return CommonResult.success(List.of());
        Map<Long, String> zones = new LinkedHashMap<>();
        jdbc.query("SELECT id,zone_code FROM oms_order_item WHERE order_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> zones.put(row.getLong("id"), row.getString("zone_code")), orderId);
        List<OmsCartItem> items = new ArrayList<>();
        for (var saved : order.getOrderItemList()) {
            ZoneCartItem item = new ZoneCartItem();
            item.setId(saved.getId());
            item.setProductId(saved.getProductId());
            item.setProductSkuId(saved.getProductSkuId());
            item.setQuantity(saved.getProductQuantity());
            item.setZoneCode(zones.get(saved.getId()));
            items.add(item);
        }
        Map<String, FullReductionOffer> offers = new LinkedHashMap<>();
        for (var item : promotions.calcCartPromotion(items)) {
            var offer = item.getFullReductionOffer();
            if (offer != null) offers.put(offer.getProductId() + ":" + offer.getZoneCode(), offer);
        }
        return CommonResult.success(new ArrayList<>(offers.values()));
    }
}
