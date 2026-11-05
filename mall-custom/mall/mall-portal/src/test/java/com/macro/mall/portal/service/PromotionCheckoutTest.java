package com.macro.mall.portal.service;

import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.service.impl.OmsPromotionServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromotionCheckoutTest {
    final PortalProductDao dao=mock(PortalProductDao.class);
    final OmsPromotionServiceImpl service=new OmsPromotionServiceImpl();
    PromotionProduct product;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"portalProductDao",dao);
        product=new PromotionProduct();product.setId(10L);product.setPrice(new BigDecimal("40.00"));
        var sku=new PmsSkuStock();sku.setId(100L);sku.setPrice(new BigDecimal("40.00"));sku.setStock(100);
        product.setSkuStockList(new ArrayList<>(List.of(sku)));product.setPromotionType(4);
        when(dao.getPromotionProductList(anyList())).thenAnswer(i->List.of(product));
    }
    ZoneCartItem item(int quantity,String zone) {
        var item=new ZoneCartItem();item.setId(1L);item.setProductId(10L);item.setProductSkuId(100L);
        item.setPrice(BigDecimal.ONE);item.setQuantity(quantity);item.setZoneCode(zone);return item;
    }
    void reduction(String threshold,String cut) {
        var reduction=new PmsProductFullReduction();reduction.setFullPrice(new BigDecimal(threshold));reduction.setReducePrice(new BigDecimal(cut));
        product.setProductFullReductionList(List.of(reduction));
    }
    BigDecimal discount(List<CartPromotionItem> lines) {
        return lines.stream().map(l->l.getReduceAmount().multiply(BigDecimal.valueOf(l.getQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    @Test void fullReductionIsExactThroughPerUnitSnapshotsAndZoneSettlement() {
        reduction("100","10");var lines=service.calcCartPromotion(List.of(item(3,"REBUY")));
        assertEquals(new BigDecimal("10.00"),discount(lines));
        assertEquals(3,lines.stream().mapToInt(CartPromotionItem::getQuantity).sum());
        assertEquals(2,lines.size()); // one unit gets the final cent, without fractional-cent DB values
        assertTrue(lines.stream().allMatch(l->l.getPrice().equals(new BigDecimal("40.00")) && l.getReduceAmount().scale()==2));
        var settlement=new ZoneSettlementService(mock(JdbcTemplate.class)).settle(lines.stream()
                .map(l->new ZoneSettlementService.Line(l.getZoneCode(),l.getPrice(),l.getQuantity(),l.getReduceAmount(),BigDecimal.ZERO)).toList(),false,false,
                new ZoneSettlementService.Wallets(0,0,BigDecimal.ZERO));
        assertEquals(new BigDecimal("110.00"),settlement.getCashAmount());
    }
    @Test void notEvenOneCentBelowThresholdReceivesReduction() {
        product.getSkuStockList().get(0).setPrice(new BigDecimal("99.99"));reduction("100","10");
        assertEquals(0,discount(service.calcCartPromotion(List.of(item(1,"REBUY")))).signum());
    }
    @Test void reductionDoesNotCombineDifferentZones() {
        reduction("60","10");
        assertEquals(0,discount(service.calcCartPromotion(List.of(item(1,"REBUY"),item(1,"CONSUME")))).signum());
    }
    @Test void membershipRoutesNeverDiscountThe3999Entitlement() {
        reduction("100","10");product.getSkuStockList().get(0).setPrice(new BigDecimal("3999.00"));
        assertEquals(0,discount(service.calcCartPromotion(List.of(item(1,"PICK")))).signum());
    }
    @Test void expiredSpecialOfferFallsBackToCurrentSkuPrice() {
        product.setPromotionType(1);product.setPromotionPrice(new BigDecimal("20"));product.setPromotionEndTime(new Date(1));
        var line=service.calcCartPromotion(List.of(item(1,"REBUY"))).get(0);
        assertEquals(new BigDecimal("40.00"),line.getPrice());assertEquals(0,line.getReduceAmount().signum());
    }
    @Test void productSpecialPriceIsUsedWhenSkuHasNoOverride() {
        product.setPromotionType(1);product.setPromotionPrice(new BigDecimal("20"));
        assertEquals(new BigDecimal("20.00"),discount(service.calcCartPromotion(List.of(item(1,"REBUY")))));
    }
    @Test void nullAndZeroConfigurationDoesNotInventDiscounts() {
        reduction("0","0");assertEquals(0,discount(service.calcCartPromotion(List.of(item(1,"REBUY")))).signum());
        product.setPromotionType(null);assertEquals(0,discount(service.calcCartPromotion(List.of(item(1,"REBUY")))).signum());
    }
    @Test void multipleSkuRoundingStillSumsToFullConfiguredDiscount() {
        product.getSkuStockList().get(0).setPrice(new BigDecimal("33.33"));
        var sku=new PmsSkuStock();sku.setId(101L);sku.setPrice(new BigDecimal("66.67"));product.getSkuStockList().add(sku);
        var second=item(1,"REBUY");second.setId(2L);second.setProductSkuId(101L);reduction("100","10");
        assertEquals(new BigDecimal("10.00"),discount(service.calcCartPromotion(List.of(item(1,"REBUY"),second))));
    }
}
