package com.macro.mall.portal.controller;

import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.OmsPromotionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FullReductionControllerTest {
    private final OmsPortalOrderService orders = mock(OmsPortalOrderService.class);
    private final OmsPromotionService promotions = mock(OmsPromotionService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FullReductionController controller = new FullReductionController(orders, promotions, jdbc);

    @Test
    void ownershipFailureStopsBeforeReadingItemsOrComputingPromotions() {
        when(orders.detail(10L)).thenThrow(new IllegalArgumentException("无权查看"));
        assertThrows(IllegalArgumentException.class, () -> controller.offers(10L));
        verifyNoInteractions(jdbc, promotions);
    }

    @Test
    void paidAndClosedOrdersDoNotOfferAReplacementPurchase() {
        for (int status : new int[] {1, 2, 3, 4, 5}) {
            var order = new OmsOrderDetail();
            order.setStatus(status);
            when(orders.detail(10L)).thenReturn(order);
            assertTrue(controller.offers(10L).getData().isEmpty());
        }
        verifyNoInteractions(jdbc, promotions);
    }
}
