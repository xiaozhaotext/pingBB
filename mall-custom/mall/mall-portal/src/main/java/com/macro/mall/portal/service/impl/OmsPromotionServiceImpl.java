package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.OmsPromotionService;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.*;
import java.util.*;

/** Catalogue prices are authoritative; reductions are exact cents and isolated by zone/SPU. */
@Service
public class OmsPromotionServiceImpl implements OmsPromotionService {
    @Autowired private PortalProductDao portalProductDao;

    @Override
    public List<CartPromotionItem> calcCartPromotion(List<OmsCartItem> items) {
        if (items.isEmpty()) return List.of();
        List<Long> ids = items.stream().map(OmsCartItem::getProductId).distinct().toList();
        Map<Long, PromotionProduct> products = new HashMap<>();
        for (var product : portalProductDao.getPromotionProductList(ids))
            products.put(product.getId(), product);
        Map<String, List<OmsCartItem>> groups = new LinkedHashMap<>();
        for (var item : items) {
            if (item.getQuantity() == null || item.getQuantity() < 1)
                throw new ApiException("商品数量无效");
            groups.computeIfAbsent(
                            item.getProductId() + ":" + zone(item), ignored -> new ArrayList<>())
                    .add(item);
        }
        List<CartPromotionItem> result = new ArrayList<>();
        for (var group : groups.values()) {
            PromotionProduct product = products.get(group.get(0).getProductId());
            if (product == null) throw new ApiException("商品已下架或不存在");
            // Existing membership rule: PICK routes cost exactly 3999 per unit, without promotions.
            int type =
                    ZoneRules.PICK.equals(zone(group.get(0)))
                            ? 0
                            : Objects.requireNonNullElse(product.getPromotionType(), 0);
            long count = group.stream().mapToLong(OmsCartItem::getQuantity).sum();
            PmsProductLadder ladder =
                    type == 3
                            ? list(product.getProductLadderList()).stream()
                                    .filter(
                                            l ->
                                                    l.getCount() != null
                                                            && l.getCount() > 0
                                                            && l.getCount() <= count
                                                            && validDiscount(l.getDiscount()))
                                    .max(
                                            Comparator.comparing(PmsProductLadder::getCount)
                                                    .thenComparing(
                                                            PmsProductLadder::getDiscount,
                                                            Comparator.reverseOrder()))
                                    .orElse(null)
                            : null;
            BigDecimal total =
                    group.stream()
                            .map(
                                    i ->
                                            price(product, sku(product, i))
                                                    .multiply(BigDecimal.valueOf(i.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            PmsProductFullReduction reduction =
                    type == 4
                            ? list(product.getProductFullReductionList()).stream()
                                    .filter(
                                            r ->
                                                    r.getFullPrice() != null
                                                            && r.getFullPrice().signum() > 0
                                                            && r.getReducePrice() != null
                                                            && r.getReducePrice().signum() > 0
                                                            && r.getReducePrice()
                                                                            .compareTo(
                                                                                    r
                                                                                            .getFullPrice())
                                                                    <= 0
                                                            && total.compareTo(r.getFullPrice())
                                                                    >= 0)
                                    .max(
                                            Comparator.comparing(
                                                            PmsProductFullReduction::getFullPrice)
                                                    .thenComparing(
                                                            PmsProductFullReduction
                                                                    ::getReducePrice))
                                    .orElse(null)
                            : null;
            if (reduction != null) {
                allocateReduction(result, group, product, total, reduction);
                continue;
            }
            for (var item : group) {
                PmsSkuStock sku = sku(product, item);
                BigDecimal original = price(product, sku), discount = BigDecimal.ZERO.setScale(2);
                String message =
                        ZoneRules.PICK.equals(zone(item))
                                ? "3999元会员路线不参与活动优惠"
                                : unmetNotice(product, type);
                if (type == 1 && promotionActive(product, new Date())) {
                    BigDecimal promotional =
                            sku.getPromotionPrice() != null
                                    ? sku.getPromotionPrice()
                                    : product.getPromotionPrice();
                    if (promotional != null
                            && promotional.signum() >= 0
                            && promotional.compareTo(original) <= 0) {
                        discount = original.subtract(promotional).setScale(2, RoundingMode.DOWN);
                        message = "特惠促销";
                    }
                } else if (ladder != null) {
                    BigDecimal payable =
                            original.multiply(ladder.getDiscount())
                                    .setScale(2, RoundingMode.HALF_UP);
                    discount = original.subtract(payable);
                    message =
                            "满"
                                    + ladder.getCount()
                                    + "件打"
                                    + ladder.getDiscount()
                                            .multiply(BigDecimal.TEN)
                                            .stripTrailingZeros()
                                            .toPlainString()
                                    + "折";
                }
                CartPromotionItem promotionLine = line(item, product, sku, discount, item.getQuantity(), message);
                if (type == 4) {
                    promotionLine.setFullReductionOffer(unmetFullReduction(product, total, zone(item)));
                }
                result.add(promotionLine);
            }
        }
        return result;
    }

    private FullReductionOffer unmetFullReduction(PromotionProduct product, BigDecimal total, String zone) {
        PmsProductFullReduction next = list(product.getProductFullReductionList()).stream()
                .filter(rule -> rule.getFullPrice() != null && rule.getFullPrice().signum() > 0
                        && rule.getReducePrice() != null && rule.getReducePrice().signum() > 0
                        && rule.getReducePrice().compareTo(rule.getFullPrice()) <= 0
                        && total.compareTo(rule.getFullPrice()) < 0)
                .min(Comparator.comparing(PmsProductFullReduction::getFullPrice)
                        .thenComparing(PmsProductFullReduction::getReducePrice, Comparator.reverseOrder()))
                .orElse(null);
        if (next == null) return null;
        FullReductionOffer offer = new FullReductionOffer();
        offer.setProductId(product.getId());
        offer.setProductName(product.getName());
        offer.setZoneCode(zone);
        offer.setCurrentAmount(total);
        offer.setThreshold(next.getFullPrice());
        offer.setDiscount(next.getReducePrice());
        offer.setGap(next.getFullPrice().subtract(total));
        return offer;
    }

    /**
     * Split only a rounding-boundary quantity so per-unit order snapshots remain exact to cents.
     */
    private void allocateReduction(
            List<CartPromotionItem> result,
            List<OmsCartItem> items,
            PromotionProduct product,
            BigDecimal total,
            PmsProductFullReduction reduction) {
        BigInteger totalCents = total.movePointRight(2).toBigIntegerExact();
        BigInteger cut =
                reduction
                        .getReducePrice()
                        .setScale(2, RoundingMode.DOWN)
                        .movePointRight(2)
                        .toBigIntegerExact();
        record Share(OmsCartItem item, PmsSkuStock sku, BigInteger base, BigInteger remainder) {}
        List<Share> shares = new ArrayList<>();
        BigInteger allocated = BigInteger.ZERO;
        for (var item : items) {
            PmsSkuStock sku = sku(product, item);
            BigInteger unit = price(product, sku).movePointRight(2).toBigIntegerExact();
            BigInteger[] divided = cut.multiply(unit).divideAndRemainder(totalCents);
            shares.add(new Share(item, sku, divided[0], divided[1]));
            allocated = allocated.add(divided[0].multiply(BigInteger.valueOf(item.getQuantity())));
        }
        shares.sort(Comparator.comparing(Share::remainder).reversed());
        int remaining = cut.subtract(allocated).intValueExact();
        String message =
                "满"
                        + reduction.getFullPrice().stripTrailingZeros().toPlainString()
                        + "元减"
                        + reduction.getReducePrice().stripTrailingZeros().toPlainString()
                        + "元";
        for (var share : shares) {
            int extra = Math.min(remaining, share.item().getQuantity());
            remaining -= extra;
            if (extra > 0)
                result.add(
                        line(
                                share.item(),
                                product,
                                share.sku(),
                                new BigDecimal(share.base().add(BigInteger.ONE), 2),
                                extra,
                                message));
            int ordinary = share.item().getQuantity() - extra;
            if (ordinary > 0)
                result.add(
                        line(
                                share.item(),
                                product,
                                share.sku(),
                                new BigDecimal(share.base(), 2),
                                ordinary,
                                message));
        }
        if (remaining != 0) throw new IllegalStateException("满减分摊失败");
    }

    private CartPromotionItem line(
            OmsCartItem item,
            PromotionProduct product,
            PmsSkuStock sku,
            BigDecimal cut,
            int quantity,
            String message) {
        CartPromotionItem line = new CartPromotionItem();
        BeanUtils.copyProperties(item, line);
        line.setPrice(price(product, sku));
        line.setQuantity(quantity);
        line.setReduceAmount(cut);
        line.setPromotionMessage(message);
        int stock = Objects.requireNonNullElse(sku.getStock(), 0),
                locked = Math.max(Objects.requireNonNullElse(sku.getLockStock(), 0), 0);
        line.setRealStock(Math.max(stock - locked, 0));
        line.setIntegration(product.getGiftPoint());
        line.setGrowth(product.getGiftGrowth());
        return line;
    }

    private PmsSkuStock sku(PromotionProduct product, OmsCartItem item) {
        return list(product.getSkuStockList()).stream()
                .filter(s -> Objects.equals(s.getId(), item.getProductSkuId()))
                .findFirst()
                .orElseThrow(() -> new ApiException("商品规格已失效"));
    }

    private BigDecimal price(PromotionProduct product, PmsSkuStock sku) {
        BigDecimal value = sku.getPrice() != null ? sku.getPrice() : product.getPrice();
        if (value == null || value.signum() < 0) throw new ApiException("商品价格无效");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String zone(OmsCartItem item) {
        return item instanceof ZoneCartItem z
                ? ZoneRules.normalize(z.getZoneCode())
                : item instanceof CartPromotionItem p
                        ? ZoneRules.normalize(p.getZoneCode())
                        : ZoneRules.NONE;
    }

    private boolean promotionActive(PromotionProduct p, Date now) {
        return (p.getPromotionStartTime() == null || !now.before(p.getPromotionStartTime()))
                && (p.getPromotionEndTime() == null || !now.after(p.getPromotionEndTime()));
    }

    private boolean validDiscount(BigDecimal v) {
        return v != null && v.signum() > 0 && v.compareTo(BigDecimal.ONE) <= 0;
    }

    private String unmetNotice(PromotionProduct product, int type) {
        if (type == 4)
            return list(product.getProductFullReductionList()).stream()
                    .filter(
                            r ->
                                    r.getFullPrice() != null
                                            && r.getFullPrice().signum() > 0
                                            && r.getReducePrice() != null
                                            && r.getReducePrice().signum() > 0
                                            && r.getReducePrice().compareTo(r.getFullPrice()) <= 0)
                    .min(Comparator.comparing(PmsProductFullReduction::getFullPrice))
                    .map(
                            r ->
                                    "满"
                                            + r.getFullPrice().stripTrailingZeros().toPlainString()
                                            + "元减"
                                            + r.getReducePrice()
                                                    .stripTrailingZeros()
                                                    .toPlainString()
                                            + "元（未达门槛，本次不减免）")
                    .orElse("无优惠");
        if (type == 3)
            return list(product.getProductLadderList()).stream()
                    .filter(
                            r ->
                                    r.getCount() != null
                                            && r.getCount() > 0
                                            && validDiscount(r.getDiscount()))
                    .min(Comparator.comparing(PmsProductLadder::getCount))
                    .map(
                            r ->
                                    "满"
                                            + r.getCount()
                                            + "件打"
                                            + r.getDiscount()
                                                    .multiply(BigDecimal.TEN)
                                                    .stripTrailingZeros()
                                                    .toPlainString()
                                            + "折（未达门槛，本次不减免）")
                    .orElse("无优惠");
        return "无优惠";
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }
}
