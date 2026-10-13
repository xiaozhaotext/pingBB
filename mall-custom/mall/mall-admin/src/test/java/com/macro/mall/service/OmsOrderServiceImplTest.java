package com.macro.mall.service;

import com.macro.mall.dao.OmsOrderDao;
import com.macro.mall.dao.OmsOrderOperateHistoryDao;
import com.macro.mall.dto.OmsOrderDeliveryParam;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderOperateHistoryMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.service.impl.OmsOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 发货与关闭订单的状态机守卫：未支付/已完成/已关闭订单不能被改坏。 */
@ExtendWith(MockitoExtension.class)
class OmsOrderServiceImplTest {
    @Mock OmsOrderMapper orderMapper;
    @Mock OmsOrderDao orderDao;
    @Mock OmsOrderOperateHistoryDao orderOperateHistoryDao;
    @Mock OmsOrderOperateHistoryMapper orderOperateHistoryMapper;
    @InjectMocks OmsOrderServiceImpl service;

    private static OmsOrder order(long id, Integer status) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setStatus(status);
        return order;
    }

    private static OmsOrderDeliveryParam deliveryParam(long orderId, String company, String sn) {
        OmsOrderDeliveryParam param = new OmsOrderDeliveryParam();
        param.setOrderId(orderId);
        param.setDeliveryCompany(company);
        param.setDeliverySn(sn);
        return param;
    }

    @Test
    void deliveryRejectsUnpaidOrder() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of(order(7L, 0)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.delivery(List.of(deliveryParam(7L, "顺丰速运", "SF1001"))));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(orderDao, never()).delivery(any());
        verify(orderOperateHistoryDao, never()).insertList(any());
    }

    @Test
    void deliveryRejectsClosedOrder() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of(order(9L, 4)));
        assertThrows(ResponseStatusException.class,
                () -> service.delivery(List.of(deliveryParam(9L, "顺丰速运", "SF1002"))));
        verify(orderDao, never()).delivery(any());
    }

    @Test
    void deliveryRejectsMissingOrder() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.delivery(List.of(deliveryParam(11L, "顺丰速运", "SF1003"))));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        verify(orderDao, never()).delivery(any());
    }

    @Test
    void deliveryRequiresTrackingNumber() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.delivery(List.of(deliveryParam(7L, "顺丰速运", " "))));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(orderMapper, orderDao, orderOperateHistoryDao);
    }

    @Test
    void deliveryFailsWhenNothingWasShipped() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of(order(7L, 1)));
        when(orderDao.delivery(any())).thenReturn(0);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.delivery(List.of(deliveryParam(7L, "顺丰速运", "SF1004"))));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(orderOperateHistoryDao, never()).insertList(any());
    }

    @Test
    void deliveryShipsWaitingOrderAndRecordsHistory() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of(order(7L, 1)));
        when(orderDao.delivery(any())).thenReturn(1);
        assertEquals(1, service.delivery(List.of(deliveryParam(7L, "顺丰速运", "SF1005"))));
        verify(orderOperateHistoryDao).insertList(any());
    }

    @Test
    void closeRejectsShippedOrder() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of(order(3L, 2)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.close(List.of(3L), "误操作"));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(orderMapper, never()).updateByExampleSelective(any(), any());
        verify(orderOperateHistoryDao, never()).insertList(any());
    }

    @Test
    void closeRejectsEmptySelection() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.close(List.of(), "空"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verifyNoInteractions(orderMapper, orderOperateHistoryDao);
    }

    @Test
    void closeClosesUnpaidOrder() {
        when(orderMapper.selectByExample(any())).thenReturn(List.of(order(3L, 0)));
        when(orderMapper.updateByExampleSelective(any(), any())).thenReturn(1);
        assertEquals(1, service.close(List.of(3L), "买家要求关闭"));
        verify(orderOperateHistoryDao).insertList(any());
    }
}
