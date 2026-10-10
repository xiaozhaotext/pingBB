package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalMockPaymentServiceTests {
    @Mock OmsOrderMapper orderMapper;
    @Mock UmsMemberService memberService;
    @Mock OmsPortalOrderService orderService;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks LocalMockPaymentService service;

    private OmsOrder order(int status, long owner) {
        OmsOrder order = new OmsOrder();
        order.setId(10L);
        order.setMemberId(owner);
        order.setStatus(status);
        order.setDeleteStatus(0);
        order.setPayAmount(new BigDecimal("100.00"));
        return order;
    }

    private void currentMember() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        when(memberService.getCurrentMember()).thenReturn(member);
    }

    @Test
    void otherMemberCannotConfirm() {
        currentMember();
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order(0, 2L));
        assertThrows(ApiException.class, () -> service.confirm(10L));
        verify(jdbcTemplate, never()).update(any(String.class), any(), any(), any());
    }

    @Test
    void repeatedConfirmationDoesNotConsumeStockAgain() {
        currentMember();
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order(1, 1L));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(10L))).thenReturn(1L);
        assertEquals("ALREADY_CONFIRMED", service.confirm(10L));
        verify(orderService, never()).paySuccess(10L, 3);
    }

    @Test
    void canceledOrderCannotRecordConfirmation() {
        currentMember();
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order(4, 1L));
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(10L))).thenReturn(0L);
        assertThrows(ApiException.class, () -> service.confirm(10L));
        verify(jdbcTemplate, never()).update(any(String.class), any(), any(), any());
    }
}
