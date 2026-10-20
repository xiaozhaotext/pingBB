package com.macro.mall.portal.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderNumberUniquenessTest {
    @Test
    void generatedNumbersUsePersistedIdsRatherThanResettableRedisCounters() throws Exception {
        Method generate = OmsPortalOrderServiceImpl.class.getDeclaredMethod("generateOrderSn", OmsOrder.class);
        generate.setAccessible(true);
        OmsOrder first = new OmsOrder();
        first.setId(147L);
        first.setSourceType(1);
        first.setPayType(0);
        OmsOrder second = new OmsOrder();
        second.setId(160L);
        second.setSourceType(1);
        second.setPayType(0);
        String firstNumber = (String) generate.invoke(new OmsPortalOrderServiceImpl(), first);
        String secondNumber = (String) generate.invoke(new OmsPortalOrderServiceImpl(), second);
        assertNotEquals(firstNumber, secondNumber);
        assertTrue(firstNumber.endsWith("000147"));
        assertTrue(secondNumber.endsWith("000160"));
    }
}

