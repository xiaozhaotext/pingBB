package com.macro.mall.service;

import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.service.impl.OmsOrderReturnApplyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 售后处理：状态跃迁必须受条件更新限制，重复完成退货不能二次退库。 */
@ExtendWith(MockitoExtension.class)
class OmsOrderReturnApplyServiceImplTest {
    @Mock OmsOrderReturnApplyDao returnApplyDao;
    @Mock OmsOrderReturnApplyMapper returnApplyMapper;
    @Mock JdbcTemplate jdbc;
    @Mock OrderReturnRefundService orderReturnRefundService;
    @InjectMocks OmsOrderReturnApplyServiceImpl service;

    private static OmsUpdateStatusParam param(Integer status) {
        OmsUpdateStatusParam param = new OmsUpdateStatusParam();
        param.setStatus(status);
        return param;
    }

    @Test
    void unsupportedStatusIsRejectedWithoutDatabaseWork() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(1L, param(9)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingParameterIsRejected() {
        assertThrows(ResponseStatusException.class, () -> service.updateStatus(null, param(1)));
        assertThrows(ResponseStatusException.class, () -> service.updateStatus(1L, param(null)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void finishReturnDoesNotSettleTwiceWhenGuardedUpdateMisses() {
        // 条件更新未命中（申请单已不在"退货中"），不得查询订单、不得归还库存。
        assertEquals(0, service.updateStatus(5L, param(2)));
        verify(jdbc, never()).queryForMap(anyString(), any());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void finishReturnRestoresStockAndClosesFullyReturnedOrder() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(anyString(), any())).thenReturn(Map.of(
                "order_id", 12L, "product_id", 26L, "product_count", 2));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
        when(orderReturnRefundService.refundForReturn(any(), any(), any(), anyString()))
                .thenReturn("退货完成，库存已归还，渠道退款已受理");
        assertEquals(1, service.updateStatus(5L, param(2)));
        // 条件更新（1 次）与按商品归还库存（1 次）参数个数相同，共 2 次
        verify(jdbc, times(2)).update(anyString(), any(), any(), any());
        // 全部退完时关闭订单（按订单号 12 定点校验，避免与其它 update 重载歧义）
        verify(jdbc).update(anyString(), eq(12L));
        // 写订单操作记录
        verify(jdbc).update(anyString(), any(), any(), any(), any());
        // 退货完成必须尝试渠道退款
        verify(orderReturnRefundService).refundForReturn(any(), any(), any(), anyString());
    }

    @Test
    void confirmReturnKeepsOrderOpen() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertEquals(1, service.updateStatus(5L, param(1)));
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any());
        verify(jdbc, never()).queryForMap(anyString(), any());
    }
}
