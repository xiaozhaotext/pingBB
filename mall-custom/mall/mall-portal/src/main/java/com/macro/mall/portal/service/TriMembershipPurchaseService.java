package com.macro.mall.portal.service;

import com.macro.mall.common.membership.TriPoolRules;
import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.portal.domain.MembershipPurchaseRequest;
import com.macro.mall.portal.domain.MembershipPaymentError;
import com.macro.mall.portal.domain.MembershipPaymentException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;

/** Standalone membership purchase from My Account; ordinary goods never activate membership. */
@Service
public class TriMembershipPurchaseService {
    private final JdbcTemplate jdbc;
    private final TriPoolService pools;
    private final PointsWalletService wallets;
    public TriMembershipPurchaseService(JdbcTemplate jdbc,TriPoolService pools,PointsWalletService wallets) {
        this.jdbc=jdbc;this.pools=pools;this.wallets=wallets;
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> prepare(long member,MembershipPurchaseRequest request) {
        if(!pools.enabled())throw new MembershipPaymentException(MembershipPaymentError.RULE_UNAVAILABLE);
        if(!"MEMBER".equals(request.level()))throw new MembershipPaymentException(MembershipPaymentError.INVALID_REQUEST);
        jdbc.queryForObject("SELECT id FROM ums_member WHERE id=? FOR UPDATE",Long.class,member);
        if(pools.isMember(member))throw new MembershipPaymentException(MembershipPaymentError.ALREADY_MEMBER);
        var existing=jdbc.queryForList("SELECT * FROM membership_purchase_order WHERE member_id=? AND request_id=?",member,request.requestId());
        if(!existing.isEmpty()) {
            var row=existing.get(0);
            if(!"MEMBER".equals(row.get("level")) || !"PENDING".equals(row.get("status"))
                    || !request.payChannel().equals(row.get("pay_channel")) || !request.payMode().equals(row.get("pay_mode")))
                throw new MembershipPaymentException(MembershipPaymentError.ORDER_CONFLICT);
            return row;
        }
        // Recover an unpaid order after browser storage was cleared or another device was used.
        // Never create a second pending order or change the frozen payment channel/amount.
        var pending=jdbc.queryForList("SELECT * FROM membership_purchase_order WHERE member_id=? AND status='PENDING' FOR UPDATE",member);
        if(!pending.isEmpty()) {
            var row=pending.get(0);
            var versions=jdbc.queryForList("SELECT rule_version FROM tri_membership_order WHERE order_id=?",String.class,row.get("id"));
            if(pending.size()!=1 || !"MEMBER".equals(row.get("level")) || versions.size()!=1 || !TriPoolRules.VERSION.equals(versions.get(0))
                    || !request.payChannel().equals(row.get("pay_channel")) || !request.payMode().equals(row.get("pay_mode")))
                throw new MembershipPaymentException(MembershipPaymentError.ORDER_CONFLICT);
            return row;
        }
        var parents=jdbc.queryForList("SELECT inviter_id FROM ums_member_recommend WHERE member_id=?",Long.class,member);
        if(parents.size()>1 || (!parents.isEmpty() && parents.get(0)==member))throw new IllegalStateException("直邀关系异常");
        String no="MP"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO membership_purchase_order(merchant_order_no,request_id,member_id,level,amount,product_points,repurchase_points,pay_channel,pay_mode,referrer_member_id,status) VALUES(?,?,?,'MEMBER',3999,3999,3999,?,?,?,'PENDING')",
                no,request.requestId(),member,request.payChannel(),request.payMode(),parents.isEmpty()?null:parents.get(0));
        var row=jdbc.queryForMap("SELECT * FROM membership_purchase_order WHERE merchant_order_no=?",no);
        jdbc.update("INSERT INTO tri_membership_order(order_id,rule_version) VALUES(?,?)",row.get("id"),TriPoolRules.VERSION);
        return row;
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void fulfill(Map<String,Object> order) {
        long id=((Number)order.get("id")).longValue(),member=((Number)order.get("member_id")).longValue();
        var versions=jdbc.queryForList("SELECT rule_version FROM tri_membership_order WHERE order_id=?",String.class,id);
        if(!pools.enabled() || !"MEMBER".equals(order.get("level")) || versions.size()!=1 || !TriPoolRules.VERSION.equals(versions.get(0))
                || TriPoolRules.PRICE.compareTo((BigDecimal)order.get("amount"))!=0
                || ((Number)order.get("product_points")).longValue()!=3999 || ((Number)order.get("repurchase_points")).longValue()!=3999)
            throw new IllegalStateException("会员规则或订单快照不一致");
        Number ref=(Number)order.get("referrer_member_id");
        pools.acceptPayment("MEMBERSHIP:"+id,member,ref==null?null:ref.longValue(),TriPoolRules.PRICE,(who,type,points,key)->{
            var result=wallets.changePoints(who,type,"earn",points,Long.toString(id),key,null,"会员购买权益");
            if(result.getCode()!=200)throw new IllegalStateException("会员积分发放失败");
            if(type==PointsWalletService.TYPE_PRODUCT)jdbc.update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?",points,who);
        });
        jdbc.update("INSERT INTO sys_audit_log(operator_name,module,action,target_id,detail) VALUES('SYSTEM:payment','membership','V2_PURCHASE_PAID',?,?)",id,"member="+member+";rule="+TriPoolRules.VERSION);
    }
}
