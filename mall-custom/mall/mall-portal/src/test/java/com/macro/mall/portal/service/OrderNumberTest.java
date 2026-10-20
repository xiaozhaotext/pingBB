package com.macro.mall.portal.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class OrderNumberTest {
    @Test void databaseIdsProduceDistinctNumbersWithoutRedisAndCannotMatchLegacyNumericNumbers() {
        var service=new OmsPortalOrderServiceImpl();
        var order=new OmsOrder();order.setSourceType(1);order.setPayType(0);order.setId(147L);
        String first=ReflectionTestUtils.invokeMethod(service,"generateOrderSn",order);
        order.setId(160L);String next=ReflectionTestUtils.invokeMethod(service,"generateOrderSn",order);
        assertNotEquals(first,next);assertTrue(first.matches("[0-9]{12}N147"));
        assertFalse(first.matches("[0-9]+"));
        order.setId(Long.MAX_VALUE);String max=ReflectionTestUtils.invokeMethod(service,"generateOrderSn",order);
        assertTrue(max.length()<=32,"Order number must fit WeChat's 32-character merchant reference limit");
    }
}
