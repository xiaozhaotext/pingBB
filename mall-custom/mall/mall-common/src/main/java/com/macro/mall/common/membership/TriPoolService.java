package com.macro.mall.common.membership;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.macro.mall.common.membership.TriPoolRules.*;

/** All three pool balances are serialized in a consistent order in the payment transaction. */
@Service
public class TriPoolService {
    private final JdbcTemplate jdbc;
    @Value("${mall.membership-v2.enabled:false}") private boolean enabled;
    public TriPoolService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean enabled() { return enabled; }
    @FunctionalInterface public interface PointsCredit {
        void post(long memberId, int walletType, long points, String key);
    }

    public boolean isMember(long memberId) {
        return enabled && !jdbc.queryForList("SELECT member_id FROM tri_member WHERE member_id=?",Long.class,memberId).isEmpty();
    }

    /** Called only by trusted payment fulfillment, with server-side frozen membership-card evidence. */
    @Transactional(rollbackFor=Exception.class)
    public boolean acceptPayment(String sourceKey, long memberId, Long referrerId,
                                 BigDecimal amount, PointsCredit points) {
        requireEnabled();
        if(sourceKey==null || !sourceKey.matches("(?:ORDER|MEMBERSHIP):[1-9][0-9]*") || memberId<=0
                || points==null)
            throw new IllegalArgumentException("会员支付凭据无效");
        long units=purchaseUnits(amount);
        lockPools();
        var existing=jdbc.queryForList("SELECT member_id,paid_amount FROM tri_payment_event WHERE source_key=? FOR UPDATE",sourceKey);
        if(!existing.isEmpty()) {
            if(number(existing.get(0),"member_id")!=memberId || decimal(existing.get(0),"paid_amount").compareTo(amount)!=0)
                throw new IllegalStateException("会员支付幂等凭据冲突");
            return false;
        }
        boolean memberExists=!jdbc.queryForList("SELECT member_id FROM tri_member WHERE member_id=? FOR UPDATE",Long.class,memberId).isEmpty();
        if(referrerId!=null && referrerId==memberId)throw new IllegalStateException("不可自我邀请");
        // Eligibility is assessed at payment: visitors receive nothing, with no later backfill.
        if(referrerId!=null && jdbc.queryForList("SELECT member_id FROM tri_member WHERE member_id=? FOR UPDATE",Long.class,referrerId).isEmpty())referrerId=null;
        jdbc.update("INSERT INTO tri_payment_event(source_key,member_id,referrer_id,paid_amount,rule_version) VALUES(?,?,?,?,?)",
                sourceKey,memberId,referrerId,amount,VERSION);
        if (!memberExists) jdbc.update("INSERT INTO tri_member(member_id,tier,direct_paid_count) VALUES(?,'MEMBER',0)",memberId);
        audit("SYSTEM:payment",null,"MEMBER_PAID",memberId,sourceKey+";units="+units+";repeat="+memberExists+";rule="+VERSION);
        points.post(memberId,1,Math.multiplyExact(BUYER_POINTS,units),"tri:"+sourceKey+":buyer:product");
        points.post(memberId,2,Math.multiplyExact(BUYER_POINTS,units),"tri:"+sourceKey+":buyer:repurchase");
        if(referrerId!=null) {
            var parent=jdbc.queryForMap("SELECT tier,direct_paid_count FROM tri_member WHERE member_id=? FOR UPDATE",referrerId);
            long count=Math.addExact(number(parent,"direct_paid_count"),units);
            Tier next=tier(true,count);
            jdbc.update("UPDATE tri_member SET direct_paid_count=?,tier=? WHERE member_id=?",count,next.name(),referrerId);
            creditEarnings(referrerId,REFERRAL_CASH.multiply(BigDecimal.valueOf(units)),"referral_reward","tri:"+sourceKey+":referral","直邀付费会员10%奖励");
            points.post(referrerId,2,Math.multiplyExact(REFERRER_POINTS,units),"tri:"+sourceKey+":referrer:repurchase");
            audit("SYSTEM:payment",null,"DIRECT_MEMBER_PAID",referrerId,
                    sourceKey+";from="+parent.get("tier")+";to="+next+";directPaid="+count+";units="+units);
        }
        for(Pool pool:Pool.values()) {
            BigDecimal in=contribution(pool).multiply(BigDecimal.valueOf(units));
            jdbc.update("UPDATE tri_pool SET balance=balance+?,total_in=total_in+? WHERE code=?",in,in,pool.name());
            jdbc.update("INSERT INTO tri_pool_ledger(pool_code,source_key,entry_type,amount,balance_after) SELECT code,?,'FUND',?,balance FROM tri_pool WHERE code=?",
                    sourceKey,in,pool.name());
        }
        return true;
    }

    /** Settle the completed Beijing calendar day; payment only funds the pools. */
    @Transactional(rollbackFor=Exception.class)
    public List<Map<String,Object>> distributeDaily(LocalDate statDate) {
        requireEnabled();
        if (statDate == null || !statDate.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
            throw new IllegalArgumentException("只能结算已结束的北京时间日期");
        lockPools();
        String key="DAILY:"+statDate;
        Timestamp cutoff=Timestamp.from(statDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        var results=new ArrayList<Map<String,Object>>();
        for (Pool pool:Pool.values()) {
            var previous=jdbc.queryForList("SELECT * FROM tri_pool_batch WHERE pool_code=? AND source_key=? FOR UPDATE",pool.name(),key);
            if (!previous.isEmpty()) { results.add(previous.get(0)); continue; }
            // Late execution must not distribute contributions from the new business day.
            BigDecimal newFunds=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM tri_pool_ledger WHERE pool_code=? AND entry_type='FUND' AND created_at>=?",BigDecimal.class,pool.name(),cutoff);
            BigDecimal budget=balance(pool).subtract(newFunds).max(BigDecimal.ZERO);
            results.add(distribute(pool,key,null,budget));
        }
        audit("SYSTEM:daily",null,"DAILY_DISTRIBUTE",null,key+";pools=PARTNER,COFOUNDER,OPERATIONS");
        return results;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> distributeOperations(String key, BigDecimal expectedBalance,
                                                   long expectedShares, String previewToken, long operatorId, String operatorName) {
        requireEnabled();
        if(key==null || !key.matches("[A-Za-z0-9_-]{16,64}") || expectedBalance==null || expectedBalance.signum()<0)
            throw new IllegalArgumentException("发放请求参数无效");
        lockPools();
        String source="ADMIN:"+key;
        var previous=jdbc.queryForList("SELECT * FROM tri_pool_batch WHERE pool_code='OPERATIONS' AND source_key=? FOR UPDATE",source);
        if(!previous.isEmpty())return previous.get(0);
        BigDecimal balance=balance(Pool.OPERATIONS);
        var positions=positions(Pool.OPERATIONS);
        long shares=positions.stream().mapToLong(Position::shares).reduce(0,Math::addExact);
        if(balance.compareTo(expectedBalance)!=0 || shares!=expectedShares
                || !token(balance,positions).equals(previewToken))
            throw new IllegalStateException("余额或份额已变化，请重新预览后发放");
        var result=distribute(Pool.OPERATIONS,source,operatorId);
        audit(operatorName,operatorId,"OPERATIONS_DISTRIBUTE",null,source+";"+result);
        return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public void setOperationsMember(long memberId,long shares,boolean active,long operatorId,String name,String reason) {
        requireEnabled();
        if(memberId<=0 || shares<1 || shares>1000000 || reason==null || reason.trim().length()<5 || reason.length()>500)
            throw new IllegalArgumentException("请填写有效会员编号、份数和5至500字调整依据");
        lockPools();
        if(jdbc.queryForList("SELECT id FROM ums_member WHERE id=?",Long.class,memberId).isEmpty())
            throw new IllegalArgumentException("用户不存在");
        var old=jdbc.queryForList("SELECT shares,enabled FROM tri_operations_member WHERE member_id=?",memberId);
        jdbc.update("INSERT INTO tri_operations_member(member_id,shares,enabled,updated_by,reason) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE shares=VALUES(shares),enabled=VALUES(enabled),updated_by=VALUES(updated_by),reason=VALUES(reason)",
                memberId,shares,active?1:0,operatorId,reason.trim());
        audit(name,operatorId,"OPERATIONS_MEMBER",memberId,"before="+old+";shares="+shares+";enabled="+active+";reason="+reason);
    }

    public Map<String,Object> operationsPreview() {
        requireEnabled();
        BigDecimal balance=balance(Pool.OPERATIONS);
        var positions=positions(Pool.OPERATIONS);
        var awards=allocate(balance,positions);
        return Map.of("balance",balance,"totalShares",positions.stream().mapToLong(Position::shares).reduce(0,Math::addExact),
                "awards",awards,"previewToken",token(balance,positions),
                "paidTotal",awards.stream().map(Award::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> memberSummary(long memberId) {
        if(!enabled)return Map.of("enabled",false);
        var rows=jdbc.queryForList("SELECT tier,direct_paid_count FROM tri_member WHERE member_id=?",memberId);
        Tier tier=rows.isEmpty()?Tier.VISITOR:Tier.valueOf(String.valueOf(rows.get(0).get("tier")));
        long refs=rows.isEmpty()?0:number(rows.get(0),"direct_paid_count");
        long units=tier==Tier.VISITOR?0:membershipUnits(memberId);
        BigDecimal totalCap=units==0?BigDecimal.ZERO:membershipCap(refs,units);
        BigDecimal partnerReceived=received(memberId,Pool.PARTNER);
        BigDecimal cofounderReceived=received(memberId,Pool.COFOUNDER);
        BigDecimal totalReceived=partnerReceived.add(cofounderReceived);
        BigDecimal totalRemaining=totalCap.subtract(totalReceived).max(BigDecimal.ZERO);
        var pools=new ArrayList<Map<String,Object>>();
        for(Pool pool:List.of(Pool.PARTNER,Pool.COFOUNDER)) {
            boolean eligible=pool==Pool.PARTNER?tier!=Tier.VISITOR:tier==Tier.COFOUNDER;
            BigDecimal received=pool==Pool.PARTNER?partnerReceived:cofounderReceived;
            pools.add(Map.of("code",pool.name(),"eligible",eligible,"shares",eligible?(pool==Pool.PARTNER?membershipShares(refs,units):units):0,
                    "cap",eligible?totalCap:BigDecimal.ZERO,"received",received,"remaining",eligible?totalRemaining:BigDecimal.ZERO));
        }
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("enabled",true);summary.put("tier",tier.name());summary.put("directPaidCount",refs);
        summary.put("membershipUnits",units);summary.put("pools",pools);summary.put("ruleVersion",VERSION);
        summary.put("customTravelEligible",tier!=Tier.VISITOR);summary.put("membershipPrice",PRICE);
        summary.put("totalCap",totalCap);summary.put("totalReceived",totalReceived);summary.put("totalRemaining",totalRemaining);
        return summary;
    }

    private Map<String,Object> distribute(Pool pool,String key,Long operator) {
        return distribute(pool,key,operator,balance(pool));
    }
    private Map<String,Object> distribute(Pool pool,String key,Long operator,BigDecimal budget) {
        BigDecimal before=balance(pool);
        var positions=positions(pool);
        var awards=allocate(before.min(budget),positions);
        BigDecimal total=awards.stream().map(Award::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        long shares=positions.stream().filter(p->p.cap()==null || p.received().compareTo(p.cap())<0)
                .mapToLong(Position::shares).reduce(0,Math::addExact);
        jdbc.update("INSERT INTO tri_pool_batch(pool_code,source_key,balance_before,balance_after,total_shares,paid_total,operator_id) VALUES(?,?,?,?,?,?,?)",
                pool.name(),key,before,before.subtract(total),shares,total,operator);
        long batch=jdbc.queryForObject("SELECT id FROM tri_pool_batch WHERE pool_code=? AND source_key=?",Long.class,pool.name(),key);
        for(Award a:awards) {
            jdbc.update("INSERT INTO tri_pool_position(member_id,pool_code,received) VALUES(?,?,0) ON DUPLICATE KEY UPDATE member_id=member_id",a.memberId(),pool.name());
            if(jdbc.update("UPDATE tri_pool_position SET received=received+? WHERE member_id=? AND pool_code=? AND received=?",a.amount(),a.memberId(),pool.name(),a.receivedBefore())!=1)
                throw new IllegalStateException("个人分红累计金额冲突");
            jdbc.update("INSERT INTO tri_pool_distribution(batch_id,pool_code,member_id,identity_snapshot,shares,total_shares,cap_amount,received_before,amount) VALUES(?,?,?,?,?,?,?,?,?)",
                    batch,pool.name(),a.memberId(),a.tier().name(),a.shares(),a.totalShares(),a.cap(),a.receivedBefore(),a.amount());
            creditEarnings(a.memberId(),a.amount(),"dividend","tri:batch:"+batch+":"+a.memberId(),pool.name()+"分红；批次"+batch);
        }
        if(total.signum()>0) {
            if(jdbc.update("UPDATE tri_pool SET balance=balance-?,total_out=total_out+? WHERE code=? AND balance>=?",total,total,pool.name(),total)!=1)
                throw new IllegalStateException("分红池余额冲突");
            jdbc.update("INSERT INTO tri_pool_ledger(pool_code,source_key,entry_type,amount,balance_after,operator_id) VALUES(?,?,'DISTRIBUTE',?,?,?)",
                    pool.name(),key,total.negate(),before.subtract(total),operator);
        }
        return Map.of("batchId",batch,"recipients",awards.size(),"paidTotal",total,"balanceAfter",before.subtract(total));
    }

    private List<Position> positions(Pool pool) {
        String sql=pool==Pool.OPERATIONS
                ?"SELECT o.member_id,COALESCE(m.tier,'VISITOR') AS tier,o.shares,COALESCE(m.direct_paid_count,0) AS direct_paid_count,COALESCE(p.received,0) AS received FROM tri_operations_member o LEFT JOIN tri_member m ON m.member_id=o.member_id LEFT JOIN tri_pool_position p ON p.member_id=o.member_id AND p.pool_code=? WHERE o.enabled=1 ORDER BY o.member_id"
                :"SELECT m.member_id,m.tier,m.direct_paid_count,COALESCE(p.received,0) AS received,"
                    +"COALESCE(other_pos.received,0) AS other_received FROM tri_member m"
                    +" LEFT JOIN tri_pool_position p ON p.member_id=m.member_id AND p.pool_code=?"
                    +" LEFT JOIN tri_pool_position other_pos ON other_pos.member_id=m.member_id AND other_pos.pool_code='"
                    +(pool==Pool.PARTNER?Pool.COFOUNDER.name():Pool.PARTNER.name())+"' "
                    +(pool==Pool.COFOUNDER?"WHERE m.tier='COFOUNDER' ":"")+"ORDER BY m.member_id";
        List<Position> result=new ArrayList<>();
        // Locking reads are essential: the outer order transaction may already have a MySQL
        // REPEATABLE READ snapshot predating another payment that committed before our pool lock.
        for(var row:jdbc.queryForList(sql+" FOR UPDATE",pool.name())) {
            long id=number(row,"member_id"),refs=number(row,"direct_paid_count");
            long units=pool==Pool.OPERATIONS?0:Math.max(1,jdbc.queryForList("SELECT paid_amount FROM tri_payment_event WHERE member_id=? FOR UPDATE",BigDecimal.class,id).stream().mapToLong(TriPoolRules::purchaseUnits).reduce(0,Math::addExact));
            result.add(new Position(id,Tier.valueOf(String.valueOf(row.get("tier"))),
                    pool==Pool.OPERATIONS?number(row,"shares"):pool==Pool.PARTNER?membershipShares(refs,units):units,
                    pool==Pool.OPERATIONS?null:poolCap(pool,refs,units,decimal(row,"other_received")),decimal(row,"received")));
        }
        return result;
    }
    /** Successful payment receipts are the authoritative unit ledger; historical members retain one unit. */
    public long membershipUnits(long memberId) {
        BigDecimal total=jdbc.queryForObject("SELECT COALESCE(SUM(paid_amount),0) FROM tri_payment_event WHERE member_id=?",BigDecimal.class,memberId);
        return total==null || total.signum()==0?1:purchaseUnits(total);
    }
    private BigDecimal received(long member,Pool pool) {
        var values=jdbc.queryForList("SELECT received FROM tri_pool_position WHERE member_id=? AND pool_code=?",BigDecimal.class,member,pool.name());
        return values.isEmpty()?BigDecimal.ZERO:values.get(0);
    }
    private BigDecimal balance(Pool pool) { return jdbc.queryForObject("SELECT balance FROM tri_pool WHERE code=?",BigDecimal.class,pool.name()); }
    private void lockPools() {
        var codes=jdbc.queryForList("SELECT code FROM tri_pool ORDER BY code FOR UPDATE",String.class);
        if(codes.size()!=3 || !new HashSet<>(codes).equals(Set.of("PARTNER","COFOUNDER","OPERATIONS")))
            throw new IllegalStateException("三分红池尚未初始化");
    }
    /** Same authoritative earnings account and ledger used by existing wallet/withdrawal screens. */
    private void creditEarnings(long member,BigDecimal amount,String type,String key,String remark) {
        jdbc.update("INSERT INTO ums_earnings(member_id,total_earnings,available_earnings,frozen_earnings,withdrawn,version) VALUES(?,0,0,0,0,0) ON DUPLICATE KEY UPDATE member_id=member_id",member);
        jdbc.queryForObject("SELECT id FROM ums_earnings WHERE member_id=? FOR UPDATE",Long.class,member);
        if(!jdbc.queryForList("SELECT id FROM ums_earnings_ledger WHERE idempotency_key=? FOR UPDATE",Long.class,key).isEmpty())
            throw new IllegalStateException("三池入账幂等记录冲突");
        jdbc.update("UPDATE ums_earnings SET total_earnings=total_earnings+?,available_earnings=available_earnings+?,version=version+1 WHERE member_id=?",amount,amount,member);
        jdbc.update("INSERT INTO ums_earnings_ledger(member_id,change_type,amount,balance_after,biz_id,idempotency_key,remark) SELECT member_id,?,?,available_earnings,?,?,? FROM ums_earnings WHERE member_id=?",
                type,amount,key,key,remark,member);
    }
    private void audit(String operator,Long id,String action,Long target,String detail) {
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,target_id,detail) VALUES(?,?,'tri_pool',?,?,?)",operator,id,action,target,detail);
    }
    private void requireEnabled() { if(!enabled)throw new IllegalStateException("新身份与三分红池尚未启用"); }
    private static String token(BigDecimal balance,List<Position> positions) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (balance.setScale(2)+"|"+positions).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static BigDecimal decimal(Map<String,Object> row,String key) { return new BigDecimal(String.valueOf(row.get(key))); }
}
