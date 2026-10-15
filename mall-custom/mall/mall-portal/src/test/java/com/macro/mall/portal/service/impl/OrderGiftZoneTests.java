package com.macro.mall.portal.service.impl;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.service.PointsWalletService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.model.UmsMember;
import com.macro.mall.common.api.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderGiftZoneTests {
    final OmsPortalOrderServiceImpl service = new OmsPortalOrderServiceImpl();
    OmsOrderItem line(String zone, int gift, int quantity) {
        OmsOrderItem i = new OmsOrderItem(); i.setZoneCode(zone); i.setGiftIntegration(gift);
        i.setProductQuantity(quantity); i.setProductId(99L); return i;
    }
    @Test void mixedOrderGiftsOnlyPickEvenWhenSameProductAppearsInAllZones() {
        Integer total = ReflectionTestUtils.invokeMethod(service,"calcGifIntegration",List.of(
            line("PICK",100,2),line("REBUY",100,3),line("CONSUME",100,4),line("",100,5)));
        assertEquals(200,total);
    }
    @Test void oldPendingNonPickLinesCannotGrantConfiguredGifts() {
        Integer total = ReflectionTestUtils.invokeMethod(service,"calcGifIntegration",List.of(
            line("REBUY",3999,1),line("CONSUME",3999,1),line(null,3999,1),line("INVALID",3999,1)));
        assertEquals(0,total);
    }
    @Test void newOrderLinesPersistZeroGiftOutsideExplicitPick() {
        for (String zone : new String[]{"REBUY","CONSUME","",null,"invalid"}) {
            CartPromotionItem cart = new CartPromotionItem(); cart.setZoneCode(zone); cart.setIntegration(100);
            OmsOrderItem item = ReflectionTestUtils.invokeMethod(service,"toOrderItem",cart);
            assertEquals(0,item.getGiftIntegration());
            if (zone == null || zone.isEmpty()) assertEquals("",item.getZoneCode());
        }
        CartPromotionItem cart = new CartPromotionItem();cart.setZoneCode("PICK");cart.setIntegration(100);
        OmsOrderItem item = ReflectionTestUtils.invokeMethod(service,"toOrderItem",cart);
        assertEquals(100,item.getGiftIntegration());
    }
    @Test void negativeGiftsDoNotSubtractAndOverflowCannotWrap() {
        Integer total=ReflectionTestUtils.invokeMethod(service,"calcGifIntegration",List.of(line("PICK",-100,1)));
        assertEquals(0,total);
        assertThrows(ArithmeticException.class,()->ReflectionTestUtils.invokeMethod(service,"calcGifIntegration",List.of(line("PICK",Integer.MAX_VALUE,2))));
    }
    @Test void paymentCreditsOnlyEligibleGiftAndUsesOrderIdempotencyKey() {
        PointsWalletService points=mock(PointsWalletService.class);
        UmsMemberService members=mock(UmsMemberService.class);
        ReflectionTestUtils.setField(service,"pointsWalletService",points);
        ReflectionTestUtils.setField(service,"memberService",members);
        when(points.changePoints(eq(1L),eq(1),eq("earn"),eq(200L),eq("10"),eq("order:gift:10"),isNull(),anyString())).thenReturn(CommonResult.success(null));
        UmsMember member=new UmsMember();member.setIntegration(50);when(members.getById(1L)).thenReturn(member);
        OmsOrderDetail order=new OmsOrderDetail();order.setMemberId(1L);
        order.setOrderItemList(List.of(line("PICK",100,2),line("CONSUME",999,1),line("REBUY",999,1)));
        ReflectionTestUtils.invokeMethod(service,"grantOrderGiftPoints",10L,order);
        verify(points).changePoints(eq(1L),eq(1),eq("earn"),eq(200L),eq("10"),eq("order:gift:10"),isNull(),anyString());
        verify(members).updateIntegration(1L,250);
    }
    @Test void nonPickPaymentDoesNotTouchWallet() {
        PointsWalletService points=mock(PointsWalletService.class);ReflectionTestUtils.setField(service,"pointsWalletService",points);
        OmsOrderDetail order=new OmsOrderDetail();order.setOrderItemList(List.of(line("CONSUME",999,1),line("REBUY",999,1)));
        ReflectionTestUtils.invokeMethod(service,"grantOrderGiftPoints",10L,order);
        verifyNoInteractions(points);
    }
}
