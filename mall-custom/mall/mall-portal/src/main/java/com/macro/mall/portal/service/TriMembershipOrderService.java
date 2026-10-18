package com.macro.mall.portal.service;

import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.ZoneRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.macro.mall.common.membership.TriPoolRules.*;

/** A verified, paid PICK route order grants membership once to a visitor. */
@Service
public class TriMembershipOrderService {
    private final TriPoolService pools;
    private final JdbcTemplate jdbc;
    private final PointsWalletService wallets;
    public TriMembershipOrderService(TriPoolService pools,JdbcTemplate jdbc,PointsWalletService wallets) {
        this.pools=pools;this.jdbc=jdbc;this.wallets=wallets;
    }
    public boolean enabled() { return pools.enabled(); }
    public boolean isMember(long id) { return pools.isMember(id); }

    /** Called in the order creation transaction, after the final payable amount is known. */
    public void snapshot(OmsOrder order,List<OmsOrderItem> items) {
        if (!enabled() || items.stream().noneMatch(i -> ZoneRules.PICK.equals(i.getZoneCode()))) return;
        for (OmsOrderItem item : items) {
            if (ZoneRules.PICK.equals(item.getZoneCode())
                    && (item.getProductPrice() == null || item.getProductPrice().compareTo(PRICE) != 0))
                throw new ApiException("自选区路线统一售价3999元，请联系平台核对商品价格");
        }
        jdbc.queryForObject("SELECT id FROM ums_member WHERE id=? FOR UPDATE",Long.class,order.getMemberId());
        if (isMember(order.getMemberId())) return;
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM tri_order_snapshot s JOIN oms_order o ON o.id=s.order_id"
                + " WHERE s.member_id=? AND o.status=0",Integer.class,order.getMemberId());
        Integer pendingCard = jdbc.queryForObject("SELECT COUNT(*) FROM membership_purchase_order"
                + " WHERE member_id=? AND status='PENDING'",Integer.class,order.getMemberId());
        if ((pending != null && pending > 0) || (pendingCard != null && pendingCard > 0))
            throw new ApiException("已有待付款会员订单，请先完成或取消原订单");
        if (items.size() != 1 || !ZoneRules.PICK.equals(items.get(0).getZoneCode())
                || !Integer.valueOf(1).equals(items.get(0).getProductQuantity())
                || order.getTotalAmount().compareTo(PRICE) != 0
                || order.getPayAmount().add(order.getBalanceAmount() == null ? BigDecimal.ZERO : order.getBalanceAmount())
                        .compareTo(PRICE) != 0)
            throw new ApiException("首次购买会员请单独选择一条3999元自选区路线，不可叠加优惠或其他商品");
        List<Long> parents = jdbc.queryForList("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?",
                Long.class, order.getMemberId());
        if (parents.size() > 1 || (!parents.isEmpty() && parents.get(0).equals(order.getMemberId())))
            throw new IllegalStateException("直邀关系异常");
        jdbc.update("INSERT INTO tri_order_snapshot(order_id,member_id,product_id,referrer_id,price,rule_version)"
                        + " VALUES(?,?,?,?,?,?)", order.getId(), order.getMemberId(),
                items.get(0).getProductId(), parents.isEmpty() ? null : parents.get(0), PRICE, VERSION);
    }

    /** Called only inside the verified payment confirmation transaction. */
    public boolean fulfill(long orderId) {
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT member_id,referrer_id,price,rule_version FROM tri_order_snapshot WHERE order_id=?", orderId);
        if (rows.isEmpty()) return false;
        if (rows.size() != 1 || !VERSION.equals(rows.get(0).get("rule_version"))
                || PRICE.compareTo((BigDecimal) rows.get(0).get("price")) != 0)
            throw new IllegalStateException("自选区会员订单快照异常");
        Map<String,Object> row = rows.get(0);
        long memberId = ((Number) row.get("member_id")).longValue();
        Number ref = (Number) row.get("referrer_id");
        return pools.acceptPayment("ORDER:" + orderId, memberId,
                ref == null ? null : ref.longValue(), PRICE, (who,type,points,key) -> {
                    var result = wallets.changePoints(who,type,"earn",points,Long.toString(orderId),key,
                            null,"自选区路线购买会员权益");
                    if (result.getCode() != 200) throw new IllegalStateException("会员积分发放失败");
                    if (type == PointsWalletService.TYPE_PRODUCT)
                        jdbc.update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?",points,who);
                });
    }
}
