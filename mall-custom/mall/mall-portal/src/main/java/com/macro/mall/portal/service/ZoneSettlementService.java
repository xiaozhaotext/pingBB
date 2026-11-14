package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.ZoneRules;
import com.macro.mall.portal.domain.ZoneSettlement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按专区隔离计算购物车/订单结算金额。
 *
 * <p>专区之间不能串用积分：自选区只能现金或余额，复购区可用复购积分、余额及现金，消费区只用外部现金。
 * 混合下单时先按专区分别计算，再汇总出最终应付金额。
 */
@Service
public class ZoneSettlementService {
    /** 结算输入行：单价与「每件」的优惠金额。 */
    public record Line(String zoneCode, BigDecimal price, int quantity,
                       BigDecimal promotionAmount, BigDecimal couponAmount) {
        BigDecimal lineGross() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }

        BigDecimal linePromotion() {
            return promotionAmount.multiply(BigDecimal.valueOf(quantity));
        }

        BigDecimal lineCoupon() {
            return couponAmount.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /** 会员可用于结算的三个账户：商品积分、复购积分、余额（充值本金，元）。 */
    public record Wallets(long productPoints, long repurchasePoints, BigDecimal balance) {
    }

    private final JdbcTemplate jdbc;

    public ZoneSettlementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 复购区复购积分抵扣比例，来自后台配置 rebuy.points.ratio（默认 30%）。 */
    public BigDecimal rebuyPointsRatio() {
        return configuredDecimal("rebuy.points.ratio", new BigDecimal("0.30"), true);
    }

    /** 每 1 商品积分抵扣的金额（元），来自后台配置 product.points.yuan_per_point。 */
    public BigDecimal productPointYuanPerPoint() {
        return configuredDecimal("product.points.yuan_per_point", BigDecimal.ONE, false);
    }

    /** 商品积分最多抵扣应付金额的比例，来自后台配置 product.points.max_ratio。 */
    public BigDecimal productPointMaxRatio() {
        return configuredDecimal("product.points.max_ratio", BigDecimal.ONE, true);
    }

    private BigDecimal configuredDecimal(String key, BigDecimal fallback, boolean ratio) {
        try {
            String raw = jdbc.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=?", String.class, key);
            if (raw != null && !raw.isBlank() && !"NULL".equalsIgnoreCase(raw.trim())) {
                BigDecimal value = new BigDecimal(raw.trim());
                if (value.signum() > 0 && (!ratio || value.compareTo(BigDecimal.ONE) <= 0)) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // 配置缺失或非数字时回退默认值，不影响已有下单链路
        }
        return fallback;
    }

    /**
     * 按专区计算结算明细。调用方按 payable 决定是否允许下单。
     *
     * @param useRepurchasePoints 复购区是否使用「复购积分 + 现金」模式（false 表示全现金）
     * @param useBalance          自选区及复购区是否使用余额（充值本金）支付
     */
    public ZoneSettlement settle(List<Line> lines, boolean useRepurchasePoints, boolean useBalance,
                                 Wallets wallets) {
        return settle(lines, useRepurchasePoints, useBalance, true, wallets);
    }

    public ZoneSettlement settle(List<Line> lines, boolean useRepurchasePoints, boolean useBalance,
                                  boolean useProductPoints, Wallets wallets) {
        BigDecimal ratio = rebuyPointsRatio();
        BigDecimal yuanPerPoint = productPointYuanPerPoint();
        BigDecimal maxRatio = productPointMaxRatio();
        Map<String, ZoneSettlement.Plan> plans = new LinkedHashMap<>();
        for (Line line : lines) {
            ZoneSettlement.Plan plan = plans.computeIfAbsent(
                    ZoneRules.settlementZone(line.zoneCode()), this::newPlan);
            plan.setQuantity(plan.getQuantity() + line.quantity());
            plan.setGrossAmount(plan.getGrossAmount().add(line.lineGross()));
            plan.setPromotionAmount(plan.getPromotionAmount().add(line.linePromotion()));
            plan.setCouponAmount(plan.getCouponAmount().add(line.lineCoupon()));
        }
        for (ZoneSettlement.Plan plan : plans.values()) {
            plan.setNetAmount(nonNegative(plan.getGrossAmount()
                    .subtract(plan.getPromotionAmount()).subtract(plan.getCouponAmount())));
        }
        // 共享同一余额账户：自选区要求余额全额支付，先为其预留，再分配给复购区。
        ZoneSettlement.Plan pick = plans.get(ZoneRules.PICK);
        if (pick != null) settlePick(pick, useBalance, wallets);
        BigDecimal availableBalance = wallets.balance() == null ? BigDecimal.ZERO : nonNegative(wallets.balance());
        if (pick != null && pick.isPayable()) {
            availableBalance = nonNegative(availableBalance.subtract(pick.getBalanceAmount()));
        }
        ZoneSettlement.Plan rebuy = plans.get(ZoneRules.REBUY);
        if (rebuy != null) settleRebuy(rebuy, useRepurchasePoints, useBalance, ratio,
                new Wallets(wallets.productPoints(), wallets.repurchasePoints(), availableBalance));
        ZoneSettlement.Plan consume = plans.get(ZoneRules.CONSUME);
        if (consume != null) settleConsume(consume, yuanPerPoint, maxRatio, useProductPoints, useBalance,
                new Wallets(wallets.productPoints(), wallets.repurchasePoints(),
                        nonNegative(availableBalance.subtract(rebuy == null ? BigDecimal.ZERO : rebuy.getBalanceAmount()))));
        ZoneSettlement settlement = new ZoneSettlement();
        for (ZoneSettlement.Plan plan : plans.values()) {
            switch (plan.getZoneCode()) {
                case ZoneRules.PICK -> { /* 已先结算以避免混合订单重复使用余额 */ }
                case ZoneRules.REBUY -> { /* 余额在自选区之后分配给复购区 */ }
                case ZoneRules.CONSUME -> { /* 消费区不使用余额或积分 */ }
                default -> throw new IllegalStateException("未知结算专区：" + plan.getZoneCode());
            }
            settlement.getPlans().add(plan);
            settlement.setTotalAmount(settlement.getTotalAmount().add(plan.getGrossAmount()));
            settlement.setPromotionAmount(settlement.getPromotionAmount().add(plan.getPromotionAmount()));
            settlement.setCouponAmount(settlement.getCouponAmount().add(plan.getCouponAmount()));
            // 汇总金额只统计可结算专区：不可结算专区（积分/余额不足）即使用户按其他方式
            // 折算也不能下单，计入「现金应付」会给出无法支付的金额，误导用户。
            if (plan.isPayable()) {
                settlement.setProductPointAmount(settlement.getProductPointAmount().add(plan.getProductPointAmount()));
                settlement.setRepurchasePointAmount(
                        settlement.getRepurchasePointAmount().add(plan.getRepurchasePointAmount()));
                settlement.setBalanceAmount(settlement.getBalanceAmount().add(plan.getBalanceAmount()));
                settlement.setCashAmount(settlement.getCashAmount().add(plan.getCashAmount()));
            }
        }
        return settlement;
    }

    private ZoneSettlement.Plan newPlan(String zoneCode) {
        ZoneSettlement.Plan plan = new ZoneSettlement.Plan();
        plan.setZoneCode(zoneCode);
        plan.setZoneLabel(ZoneRules.label(zoneCode));
        plan.setZoneRule(ZoneRules.ruleLabel(zoneCode));
        return plan;
    }

    /** 自选区：现金或余额（充值本金）全额支付，不能使用任何积分。 */
    private void settlePick(ZoneSettlement.Plan plan, boolean useBalance, Wallets wallets) {
        BigDecimal net = plan.getNetAmount();
        if (!useBalance) {
            plan.setCashAmount(net);
            return;
        }
        BigDecimal balance = wallets.balance() == null ? BigDecimal.ZERO : wallets.balance();
        if (balance.compareTo(net) < 0) {
            plan.setPayable(false);
            plan.setBlockedReason("余额不足，自选区商品需要 " + net.toPlainString()
                    + " 元余额，可改用现金支付");
            plan.setCashAmount(net);
            return;
        }
        plan.setBalanceAmount(net);
        plan.setCashAmount(BigDecimal.ZERO);
    }

    /** 复购区：先用复购积分抵扣固定比例，再用可用余额，最后的差额由外部支付。 */
    private void settleRebuy(ZoneSettlement.Plan plan, boolean useRepurchasePoints, boolean useBalance,
                             BigDecimal ratio, Wallets wallets) {
        BigDecimal net = plan.getNetAmount();
        if (!useRepurchasePoints) {
            applyRebuyBalance(plan, net, useBalance, wallets.balance());
            return;
        }
        BigDecimal cap = net.multiply(ratio).setScale(2, RoundingMode.DOWN);
        long maxPoints = cap.setScale(0, RoundingMode.DOWN).longValueExact();
        if (maxPoints <= 0) {
            // Integer points cannot cover a fractional point. The remaining price is still payable.
            applyRebuyBalance(plan, net, useBalance, wallets.balance());
            return;
        }
        if (wallets.repurchasePoints() < maxPoints) {
            plan.setPayable(false);
            plan.setBlockedReason("复购积分不足，复购区需要 " + maxPoints + " 积分，可改用全现金支付");
            plan.setCashAmount(net);
            return;
        }
        BigDecimal deduct = BigDecimal.valueOf(maxPoints);
        plan.setRepurchasePoints(maxPoints);
        plan.setRepurchasePointAmount(deduct);
        applyRebuyBalance(plan, nonNegative(net.subtract(deduct)), useBalance, wallets.balance());
    }

    private void applyRebuyBalance(ZoneSettlement.Plan plan, BigDecimal remaining,
                                   boolean useBalance, BigDecimal availableBalance) {
        BigDecimal balance = useBalance && availableBalance != null
                ? nonNegative(availableBalance).min(remaining) : BigDecimal.ZERO;
        plan.setBalanceAmount(balance);
        plan.setCashAmount(nonNegative(remaining.subtract(balance)));
    }

    /** 消费区使用外部现金支付，不再消耗商品积分或账户余额。 */
    private void settleConsume(ZoneSettlement.Plan plan, BigDecimal yuanPerPoint,
                               BigDecimal maxRatio, boolean useProductPoints, boolean useBalance, Wallets wallets) {
        plan.setProductPoints(0);
        plan.setProductPointAmount(BigDecimal.ZERO);
        plan.setBalanceAmount(BigDecimal.ZERO);
        plan.setCashAmount(nonNegative(plan.getNetAmount()));
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        return rounded.signum() < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : rounded;
    }
}
