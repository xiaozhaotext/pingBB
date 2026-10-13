package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.service.OmsOrderReturnApplyService;
import com.macro.mall.service.OrderReturnRefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 订单退货管理Service实现类
 * Created by macro on 2018/10/18.
 */
@Service
public class OmsOrderReturnApplyServiceImpl implements OmsOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyDao returnApplyDao;
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrderReturnRefundService orderReturnRefundService;
    @Override
    public List<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        return returnApplyDao.getList(queryParam);
    }

    @Override
    public int delete(List<Long> ids) {
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andIdIn(ids).andStatusEqualTo(3);
        return returnApplyMapper.deleteByExample(example);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStatus(Long id, OmsUpdateStatusParam statusParam) {
        Integer status = statusParam == null ? null : statusParam.getStatus();
        if (id == null || status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "售后处理参数无效");
        }
        if (status == 1) {
            return confirmReturn(id, statusParam);
        }
        if (status == 2) {
            return finishReturn(id, statusParam);
        }
        if (status == 3) {
            return rejectReturn(id, statusParam);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的售后状态: " + status);
    }

    /** 确认退货：只允许 待处理(0) -> 退货中(1)，状态已变化的申请不会被覆盖。 */
    private int confirmReturn(Long id, OmsUpdateStatusParam statusParam) {
        return jdbc.update("UPDATE oms_order_return_apply SET status=1,return_amount=?,company_address_id=?,"
                        + "handle_time=NOW(),handle_man=?,handle_note=? WHERE id=? AND status=0",
                statusParam.getReturnAmount(), statusParam.getCompanyAddressId(),
                statusParam.getHandleMan(), statusParam.getHandleNote(), id);
    }

    /** 拒绝退货：只允许 待处理(0) -> 已拒绝(3)。 */
    private int rejectReturn(Long id, OmsUpdateStatusParam statusParam) {
        return jdbc.update("UPDATE oms_order_return_apply SET status=3,"
                        + "handle_time=NOW(),handle_man=?,handle_note=? WHERE id=? AND status=0",
                statusParam.getHandleMan(), statusParam.getHandleNote(), id);
    }

    /**
     * 完成退货：只有 退货中(1) -> 已完成(2) 的那一次才归还库存并结算订单。
     * 条件更新保证重复提交不会二次退库；订单内全部商品都退完时才关闭订单，部分退货不改订单状态。
     */
    private int finishReturn(Long id, OmsUpdateStatusParam statusParam) {
        int updated = jdbc.update("UPDATE oms_order_return_apply SET status=2,"
                        + "receive_time=NOW(),receive_man=?,receive_note=? WHERE id=? AND status=1",
                statusParam.getReceiveMan(), statusParam.getReceiveNote(), id);
        if (updated != 1) {
            return updated;
        }
        Map<String, Object> apply = jdbc.queryForMap(
                "SELECT order_id,product_id,product_count,return_amount FROM oms_order_return_apply WHERE id=?", id);
        Long orderId = asLong(apply.get("order_id"));
        Long productId = asLong(apply.get("product_id"));
        Long productCount = asLong(apply.get("product_count"));
        if (orderId == null || productId == null || productCount == null || productCount <= 0) {
            return updated;
        }
        // 退货商品对应的库存是支付时扣减的，这里按退货数量归还。
        jdbc.update("UPDATE pms_sku_stock s JOIN oms_order_item i ON i.product_sku_id = s.id"
                        + " SET s.stock = s.stock + ?"
                        + " WHERE i.order_id = ? AND i.product_id = ? AND i.product_sku_id IS NOT NULL",
                productCount, orderId, productId);
        String operator = statusParam.getReceiveMan() == null || statusParam.getReceiveMan().isBlank()
                ? "后台管理员" : statusParam.getReceiveMan();
        Long pendingItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oms_order_item i WHERE i.order_id=? AND i.product_quantity > COALESCE("
                        + "(SELECT SUM(r.product_count) FROM oms_order_return_apply r"
                        + " WHERE r.order_id=i.order_id AND r.product_id=i.product_id AND r.status=2),0)",
                Long.class, orderId);
        boolean fullyReturned = pendingItems == null || pendingItems == 0;
        int closed = 0;
        if (fullyReturned) {
            closed = jdbc.update("UPDATE oms_order SET status=4"
                    + " WHERE id=? AND delete_status=0 AND status IN (1,2)", orderId);
        }
        String refundNote = orderReturnRefundService.refundForReturn(
                id, orderId, asDecimal(apply.get("return_amount")), "订单退货退款");
        jdbc.update("INSERT INTO oms_order_operate_history(order_id,operate_man,create_time,order_status,note)"
                        + " VALUES(?,?,NOW(),?,?)",
                orderId, operator, fullyReturned ? 4 : null,
                fullyReturned && closed == 1 ? refundNote + "，订单已关闭" : refundNote);
        return updated;
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static java.math.BigDecimal asDecimal(Object value) {
        return value instanceof java.math.BigDecimal decimal ? decimal
                : (value instanceof Number number ? java.math.BigDecimal.valueOf(number.doubleValue()) : null);
    }

    @Override
    public OmsOrderReturnApplyResult getItem(Long id) {
        return returnApplyDao.getDetail(id);
    }
}
