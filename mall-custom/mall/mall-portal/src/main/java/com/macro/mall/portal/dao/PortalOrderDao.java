package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台订单管理自定义Dao
 * Created by macro on 2018/9/4.
 */
public interface PortalOrderDao {
    /**
     * 获取订单及下单商品详情
     */
    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    /**
     * 修改 pms_sku_stock表的锁定库存及真实库存
     */
    int updateSkuStock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 获取超时订单
     * @param minute 超时时间（分）
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute);

    /**
     * 批量修改订单状态
     */
    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 解除取消订单的库存锁定
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /** 原子预占；库存不足或已有预占时返回 0。 */
    int reserveSku(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 仅从已预占数量扣减，防止重复支付造成负库存。 */
    int consumeReservedSku(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 仅释放仍存在的预占数量。 */
    int releaseReservedSku(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 支付状态仅能从待支付迁移。 */
    int markPaidIfPending(@Param("orderId") Long orderId, @Param("payType") Integer payType);

    /** 取消状态仅能从待支付迁移。 */
    int markCanceledIfPending(@Param("orderId") Long orderId);

}
