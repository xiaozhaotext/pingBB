package com.macro.mall.portal.service.impl;

import com.macro.mall.portal.dao.BizCofounderOrderMapper;
import com.macro.mall.portal.dao.BizPartnerOrderMapper;
import com.macro.mall.portal.domain.BizCofounderOrder;
import com.macro.mall.portal.domain.BizPartnerOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class P0SimulatedOrderOwnershipTests {
    @Mock BizPartnerOrderMapper partnerOrders;
    @Mock BizCofounderOrderMapper cofounderOrders;
    @InjectMocks PartnerServiceImpl partner;
    @InjectMocks CofounderServiceImpl cofounder;

    @Test
    void partnerCannotPayOtherMembersOrder() {
        BizPartnerOrder order = new BizPartnerOrder();
        order.setMemberId(2L);
        order.setBizId("other-partner-order");
        order.setPayStatus(0);
        when(partnerOrders.selectByBizId(order.getBizId())).thenReturn(order);

        assertThrows(ResponseStatusException.class,
                () -> partner.confirmSimulatePay(1L, order.getBizId()));
        verify(partnerOrders, never()).updateStatus(order);
    }

    @Test
    void cofounderCannotPayOtherMembersOrder() {
        BizCofounderOrder order = new BizCofounderOrder();
        order.setMemberId(2L);
        order.setBizId("other-cofounder-order");
        order.setPayStatus(0);
        when(cofounderOrders.selectByBizId(order.getBizId())).thenReturn(order);

        assertThrows(ResponseStatusException.class,
                () -> cofounder.confirmSimulatePay(1L, order.getBizId()));
        verify(cofounderOrders, never()).updateStatus(order);
    }
}
