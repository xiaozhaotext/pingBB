package com.macro.mall.portal.service.impl;

import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.model.OmsOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class PortalOrderStockConcurrencyTests {
    @Autowired private PortalOrderDao orderDao;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OmsPortalOrderService orderService;
    @Autowired private PaymentExceptionRecorder exceptionRecorder;

    @Test
    void twentyConcurrentReservationsNeverOversellFiveUnits() throws Exception {
        Long sku = createSku(5);
        int accepted = race(20, () -> orderDao.reserveSku(sku, 1));
        assertEquals(5, accepted);
        assertEquals(5, stock(sku, "stock"));
        assertEquals(5, stock(sku, "lock_stock"));
        assertEquals(0, orderDao.reserveSku(sku, 1));
    }

    @Test
    void competingPaymentAndReleaseConsumeOneReservationOnce() throws Exception {
        Long sku = createSku(1);
        assertEquals(1, orderDao.reserveSku(sku, 1));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> paid = pool.submit(() -> { start.await(); return orderDao.consumeReservedSku(sku, 1); });
            Future<Integer> canceled = pool.submit(() -> { start.await(); return orderDao.releaseReservedSku(sku, 1); });
            start.countDown();
            assertEquals(1, paid.get() + canceled.get());
            assertEquals(0, stock(sku, "lock_stock"));
            assertTrue(stock(sku, "stock") >= 0);
            assertEquals(0, orderDao.consumeReservedSku(sku, 1));
            assertEquals(0, orderDao.releaseReservedSku(sku, 1));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simulatedVerifiedPaymentIsIdempotentAndCanceledOrderCannotResurrect() {
        Long paidSku = createSku(1);
        Long paidOrder = createOrder(paidSku);
        assertEquals(1, orderDao.reserveSku(paidSku, 1));
        assertEquals(1, orderService.paySuccess(paidOrder, 1));
        assertEquals(0, orderService.paySuccess(paidOrder, 1));
        assertEquals(0, stock(paidSku, "stock"));
        assertEquals(0, stock(paidSku, "lock_stock"));

        Long canceledSku = createSku(1);
        Long canceledOrder = createOrder(canceledSku);
        assertEquals(1, orderDao.reserveSku(canceledSku, 1));
        orderService.cancelOrder(canceledOrder);
        assertEquals(4, jdbc.queryForObject("SELECT status FROM oms_order WHERE id=?", Integer.class, canceledOrder));
        assertEquals(0, stock(canceledSku, "lock_stock"));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> orderService.paySuccess(canceledOrder, 1));
        assertEquals(4, jdbc.queryForObject("SELECT status FROM oms_order WHERE id=?", Integer.class, canceledOrder));
    }

    @Test
    void latePaymentCreatesOneDurableRefundReviewTask() {
        Long orderId = createOrder(createSku(1));
        jdbc.update("UPDATE oms_order SET status=4 WHERE id=?", orderId);
        OmsOrder order = new OmsOrder();
        order.setId(orderId);
        order.setOrderSn(jdbc.queryForObject("SELECT order_sn FROM oms_order WHERE id=?", String.class, orderId));
        String trade = "mock-" + UUID.randomUUID();
        exceptionRecorder.record(order, trade, new BigDecimal("1.00"), "LATE_PAYMENT_CLOSED_ORDER");
        exceptionRecorder.record(order, trade, new BigDecimal("1.00"), "LATE_PAYMENT_CLOSED_ORDER");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_payment_exception WHERE channel_trade_no=?", Integer.class, trade));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_refund_compensation c "
                + "JOIN oms_payment_exception e ON e.id=c.payment_exception_id WHERE e.channel_trade_no=? "
                + "AND c.state='WAITING_MANUAL_REVIEW'", Integer.class, trade));
    }

    private Long createOrder(Long sku) {
        String sn = "P3A" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO oms_order(member_id,order_sn,receiver_name,receiver_phone,status,delete_status,pay_amount) "
                + "VALUES(1,?,'test','000',0,0,1.00)", sn);
        Long id = jdbc.queryForObject("SELECT id FROM oms_order WHERE order_sn=?", Long.class, sn);
        jdbc.update("INSERT INTO oms_order_item(order_id,order_sn,product_sku_id,product_quantity) VALUES(?,?,?,1)", id, sn, sku);
        return id;
    }

    private Long createSku(int quantity) {
        String code = "phase3a-" + UUID.randomUUID();
        jdbc.update("INSERT INTO pms_sku_stock(sku_code,stock,lock_stock) VALUES(?,?,0)", code, quantity);
        return jdbc.queryForObject("SELECT id FROM pms_sku_stock WHERE sku_code=?", Long.class, code);
    }

    private int stock(Long sku, String field) {
        return jdbc.queryForObject("SELECT " + field + " FROM pms_sku_stock WHERE id=?", Integer.class, sku);
    }

    private int race(int n, Callable<Integer> action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> { start.await(); return action.call(); }));
            }
            start.countDown();
            int count = 0;
            for (Future<Integer> result : results) count += result.get();
            return count;
        } finally {
            pool.shutdownNow();
        }
    }
}
