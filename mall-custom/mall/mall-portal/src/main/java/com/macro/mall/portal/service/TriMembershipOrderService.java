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

/** Only an explicitly designated, standalone PICK membership-card order activates membership. */
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

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void snapshot(OmsOrder order,List<OmsOrderItem> items) {
        if(!enabled())return;
        Set<Long> designated=new HashSet<>(jdbc.queryForList("SELECT product_id FROM tri_membership_product WHERE enabled=1",Long.class));
        if(items.stream().noneMatch(i->designated.contains(i.getProductId())))return;
        if(items.size()!=1 || !Integer.valueOf(1).equals(items.get(0).getProductQuantity())
                || !ZoneRules.PICK.equals(items.get(0).getZoneCode())
                || order.getPayAmount()==null || order.getPayAmount().add(order.getBalanceAmount()==null?BigDecimal.ZERO:order.getBalanceAmount()).compareTo(PRICE)!=0
                || items.get(0).getProductPrice()==null || items.get(0).getProductPrice().compareTo(PRICE)!=0)
            throw new ApiException("3999元身份牌需自选区单独购买一张，不可混单或优惠抵扣");
        // Serialize checkouts for one buyer and use current locking reads, not a stale transaction snapshot.
        jdbc.queryForObject("SELECT id FROM ums_member WHERE id=? FOR UPDATE",Long.class,order.getMemberId());
        if(!jdbc.queryForList("SELECT member_id FROM tri_member WHERE member_id=? FOR UPDATE",Long.class,order.getMemberId()).isEmpty())
            throw new ApiException("您已是会员，无需重复购买身份牌");
        if(!jdbc.queryForList("SELECT s.order_id FROM tri_order_snapshot s JOIN oms_order o ON o.id=s.order_id WHERE s.member_id=? AND o.status IN (0,1,2,3) FOR UPDATE",Long.class,order.getMemberId()).isEmpty())
            throw new ApiException("已有会员身份牌订单，请先支付或取消原订单");
        // Freeze the direct relation, while the engine checks paid-member eligibility at payment time.
        var parents=jdbc.queryForList("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?",Long.class,order.getMemberId());
        if(parents.size()>1 || (!parents.isEmpty() && parents.get(0).equals(order.getMemberId())))
            throw new IllegalStateException("直邀关系异常");
        jdbc.update("INSERT INTO tri_order_snapshot(order_id,member_id,product_id,referrer_id,price,rule_version) VALUES(?,?,?,?,?,?)",
                order.getId(),order.getMemberId(),items.get(0).getProductId(),parents.isEmpty()?null:parents.get(0),PRICE,VERSION);
    }

    @Transactional(rollbackFor=Exception.class)
    public boolean fulfill(long orderId) {
        if(!enabled())return false;
        var rows=jdbc.queryForList("SELECT s.*,o.pay_amount,o.balance_amount,o.status FROM tri_order_snapshot s JOIN oms_order o ON o.id=s.order_id WHERE s.order_id=? FOR UPDATE",orderId);
        if(rows.isEmpty())return false;
        var row=rows.get(0);
        if(!VERSION.equals(row.get("rule_version")) || ((Number)row.get("status")).intValue()!=1
                || PRICE.compareTo(new BigDecimal(String.valueOf(row.get("price"))))!=0)
            throw new IllegalStateException("会员身份牌支付快照不一致");
        BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(SUM(paid_amount),0) FROM oms_payment_confirmation WHERE order_id=?",BigDecimal.class,orderId);
        BigDecimal balance=row.get("balance_amount")==null?BigDecimal.ZERO:new BigDecimal(String.valueOf(row.get("balance_amount")));
        BigDecimal cash=new BigDecimal(String.valueOf(row.get("pay_amount")));
        if(paid==null || paid.compareTo(cash)!=0 || balance.signum()<0 || cash.signum()<0
                || PRICE.compareTo(paid.add(balance))!=0)throw new IllegalStateException("会员支付确认金额不一致");
        if(balance.signum()>0) {
            Long spent=jdbc.queryForObject("SELECT COALESCE(-SUM(amount_cents),0) FROM ums_recharge_ledger WHERE member_id=? AND biz_id=? AND change_type IN ('order_pay','order_refund')",Long.class,
                    ((Number)row.get("member_id")).longValue(),Long.toString(orderId));
            if(spent==null || spent!=balance.movePointRight(2).longValueExact())
                throw new IllegalStateException("会员身份牌余额扣减凭据不一致");
        }
        Number ref=(Number)row.get("referrer_id");
        pools.acceptPayment("ORDER:"+orderId,((Number)row.get("member_id")).longValue(),
                ref==null?null:ref.longValue(),PRICE,(member,type,points,key)->{
                    var result=wallets.changePoints(member,type,"earn",points,Long.toString(orderId),key,null,"会员身份牌权益");
                    if(result.getCode()!=200)throw new IllegalStateException("会员积分发放失败："+result.getMessage());
                    if(type==PointsWalletService.TYPE_PRODUCT)
                        jdbc.update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?",points,member);
                });
        return true;
    }
}
