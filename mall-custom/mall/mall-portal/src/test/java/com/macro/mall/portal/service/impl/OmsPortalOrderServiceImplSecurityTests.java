package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.domain.OrderParam;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class OmsPortalOrderServiceImplSecurityTests {
    @Mock UmsMemberService memberService;
    @Mock OmsOrderMapper orderMapper;
    @Mock OmsOrderItemMapper orderItemMapper;
    @Mock PortalOrderDao portalOrderDao;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks OmsPortalOrderServiceImpl service;

    private void otherMemberOrder() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        OmsOrder order = new OmsOrder();
        order.setId(10L);
        order.setMemberId(2L);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order);
    }

    @Test
    void cannotReadAnotherMembersOrder() {
        otherMemberOrder();
        assertThrows(ApiException.class, () -> service.detail(10L));
        verify(orderItemMapper, never()).selectByExample(any());
    }

    @Test
    void cannotCancelAnotherMembersOrder() {
        otherMemberOrder();
        assertThrows(ApiException.class, () -> service.cancelUserOrder(10L));
        verify(portalOrderDao, never()).markCanceledIfPending(10L);
    }

    @Test
    void paidOrderCannotBeCanceledByMember() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        OmsOrder order = new OmsOrder();
        order.setId(10L);
        order.setMemberId(1L);
        order.setStatus(1);
        order.setDeleteStatus(0);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order);
        assertThrows(ApiException.class, () -> service.cancelUserOrder(10L));
        verify(portalOrderDao, never()).markCanceledIfPending(10L);
    }

    @Test
    void repeatedPaymentDoesNotConsumeStockAgain() {
        OmsOrder order = new OmsOrder();
        order.setStatus(1);
        when(portalOrderDao.markPaidIfPending(10L, 1)).thenReturn(0);
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order);
        assertEquals(0, service.paySuccess(10L, 1));
        verify(portalOrderDao, never()).getDetail(10L);
    }

    @Test
    void canceledOrderCannotBecomePaid() {
        OmsOrder order = new OmsOrder();
        order.setStatus(4);
        when(portalOrderDao.markPaidIfPending(10L, 1)).thenReturn(0);
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order);
        assertThrows(ApiException.class, () -> service.paySuccess(10L, 1));
        verify(portalOrderDao, never()).getDetail(10L);
    }

    @Test
    void duplicateOrderRequestReturnsOriginalOrderWithoutReservingAgain() {
        UmsMember member = new UmsMember();
        member.setId(1L);
        when(memberService.getCurrentMember()).thenReturn(member);
        OrderParam request = new OrderParam();
        String key = "phase3a_repeat_12345";
        String hash = DigestUtil.sha256Hex(JSONUtil.toJsonStr(request));
        when(jdbcTemplate.update(any(String.class), eq(1L), eq(key), eq(hash)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbcTemplate.queryForList(any(String.class), eq(1L), eq(key)))
                .thenReturn(List.of(Map.of("request_hash", hash, "order_id", 10L, "state", "COMPLETED")));
        OmsOrder order = new OmsOrder();
        order.setId(10L);
        order.setMemberId(1L);
        when(orderMapper.selectByPrimaryKey(10L)).thenReturn(order);
        assertEquals(order, service.generateOrder(request, key).get("order"));
        verify(portalOrderDao, never()).reserveSku(any(), any());
    }

    @Test
    void invalidIdempotencyKeyIsRejectedBeforeDatabaseWrite() {
        assertThrows(ApiException.class, () -> service.generateOrder(new OrderParam(), "short"));
        verify(jdbcTemplate, never()).update(any(String.class), any(), any(), any());
    }
}
