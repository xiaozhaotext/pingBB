package com.macro.mall.portal.service;

import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.ZoneRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import static com.macro.mall.common.membership.TriPoolRules.*;

/** A verified, paid PICK route order grants one additional membership unit. */
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
        if (items.stream().noneMatch(i -> ZoneRules.PICK.equals(i.getZoneCode()))) return;
        if (!enabled()) throw new ApiException("旅游路线会员权益暂不可用，请稍后再购买");
        for (OmsOrderItem item : items) {
            if (ZoneRules.PICK.equals(item.getZoneCode())
                    && (item.getProductPrice() == null || item.getProductPrice().compareTo(PRICE) != 0))
                throw new ApiException("自选区路线统一售价3999元，请联系平台核对商品价格");
            if (ZoneRules.PICK.equals(item.getZoneCode())) {
                Integer approved = jdbc.queryForObject("SELECT COUNT(*) FROM pms_product p"
                        + " JOIN pms_product_zone z ON z.product_id=p.id AND z.zone_code='PICK'"
                        + " JOIN tri_membership_product t ON t.product_id=p.id AND t.enabled=1"
                        + " JOIN pms_sku_stock s ON s.id=? AND s.product_id=p.id"
                        + " WHERE p.id=? AND p.publish_status=1 AND p.delete_status=0"
                        + " AND p.price=3999.00 AND s.price=3999.00", Integer.class,
                        item.getProductSkuId(), item.getProductId());
                if (approved == null || approved != 1)
                    throw new ApiException("自选区仅支持已上架的3999元旅游路线");
            }
        }
        jdbc.queryForObject("SELECT id FROM ums_member WHERE id=? FOR UPDATE",Long.class,order.getMemberId());
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM tri_order_snapshot s JOIN oms_order o ON o.id=s.order_id"
                + " WHERE s.member_id=? AND o.status=0",Integer.class,order.getMemberId());
        Integer pendingCard = jdbc.queryForObject("SELECT COUNT(*) FROM membership_purchase_order"
                + " WHERE member_id=? AND status='PENDING'",Integer.class,order.getMemberId());
        if ((pending != null && pending > 0) || (pendingCard != null && pendingCard > 0))
            throw new ApiException("已有待付款会员订单，请先完成或取消原订单");
        long units=0;
        for(OmsOrderItem item:items) {
            if(!ZoneRules.PICK.equals(item.getZoneCode()) || item.getProductQuantity()==null || item.getProductQuantity()<1)
                throw new ApiException("会员权益订单仅支持自选区3999元路线，不可混合其他区域商品");
            units=Math.addExact(units,item.getProductQuantity().longValue());
        }
        BigDecimal total=PRICE.multiply(BigDecimal.valueOf(units));
        if(order.getTotalAmount()==null || order.getPayAmount()==null
                || order.getTotalAmount().compareTo(total)!=0
                || order.getPayAmount().add(order.getBalanceAmount()==null?BigDecimal.ZERO:order.getBalanceAmount()).compareTo(total)!=0)
            throw new ApiException("会员权益按每份3999元购买，不可叠加优惠");
        List<Long> parents = jdbc.queryForList("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?",
                Long.class, order.getMemberId());
        if (parents.size() > 1 || (!parents.isEmpty() && parents.get(0).equals(order.getMemberId())))
            throw new IllegalStateException("直邀关系异常");
        jdbc.update("INSERT INTO tri_order_snapshot(order_id,member_id,product_id,referrer_id,price,rule_version)"
                        + " VALUES(?,?,?,?,?,?)", order.getId(), order.getMemberId(),
                items.get(0).getProductId(), parents.isEmpty() ? null : parents.get(0), total, VERSION);
    }

    /** Called only inside the verified payment confirmation transaction. */
    public boolean fulfill(long orderId) {
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT member_id,referrer_id,price,rule_version FROM tri_order_snapshot WHERE order_id=?", orderId);
        if (rows.isEmpty()) return false;
        if (rows.size() != 1 || !VERSION.equals(rows.get(0).get("rule_version"))
                || rows.get(0).get("price")==null)
            throw new IllegalStateException("自选区会员订单快照异常");
        Map<String,Object> row = rows.get(0);
        BigDecimal amount=(BigDecimal)row.get("price");
        purchaseUnits(amount);
        long memberId = ((Number) row.get("member_id")).longValue();
        Number ref = (Number) row.get("referrer_id");
        return pools.acceptPayment("ORDER:" + orderId, memberId,
                ref == null ? null : ref.longValue(), amount, (who,type,points,key) -> {
                    var result = wallets.changePoints(who,type,"earn",points,Long.toString(orderId),key,
                            null,"自选区路线购买会员权益");
                    if (result.getCode() != 200) throw new IllegalStateException("会员积分发放失败");
                    if (type == PointsWalletService.TYPE_PRODUCT)
                        jdbc.update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?",points,who);
                });
    }
}
