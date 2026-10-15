package com.macro.mall.portal.domain;

/**
 * 三区购买规则：自选区、复购区、消费区。
 *
 * <p>规则隔离要求不同专区的商品不能混用其他专区的结算方式：
 * <ul>
 *   <li>PICK 自选区：现金或余额（充值本金）购买，不能使用产品积分或复购积分。</li>
 *   <li>REBUY 复购区：复购积分（默认抵扣 30%）+ 现金，不能使用产品积分或余额。</li>
 *   <li>CONSUME 消费区：仅用商品积分购买，不能使用复购积分、余额或现金。</li>
 *   <li>空专区：商品未加入任何专区，按自选区规则结算，同样不能使用消费区/复购区积分。</li>
 * </ul>
 */
public final class ZoneRules {
    public static final String PICK = "PICK";
    public static final String REBUY = "REBUY";
    public static final String CONSUME = "CONSUME";
    /** 未加入任何专区的商品：按自选区规则结算。 */
    public static final String NONE = "";

    private ZoneRules() {
    }

    /** 归一化专区标识：未知或空值都按自选区规则处理。 */
    public static String normalize(String zoneCode) {
        if (zoneCode == null) {
            return NONE;
        }
        String upper = zoneCode.trim().toUpperCase();
        if (PICK.equals(upper) || REBUY.equals(upper) || CONSUME.equals(upper)) {
            return upper;
        }
        return NONE;
    }

    /** 后端可管理的专区标识（不含未分配）。 */
    public static boolean isManageable(String zoneCode) {
        return PICK.equals(zoneCode) || REBUY.equals(zoneCode) || CONSUME.equals(zoneCode);
    }

    /** 结算分区：空专区与自选区同规则，合并展示与计算。 */
    public static String settlementZone(String zoneCode) {
        String normalized = normalize(zoneCode);
        return NONE.equals(normalized) ? PICK : normalized;
    }

    public static String label(String zoneCode) {
        String normalized = normalize(zoneCode);
        return switch (normalized) {
            case PICK -> "自选区";
            case REBUY -> "复购区";
            case CONSUME -> "消费区";
            default -> "普通商品";
        };
    }

    /** 专区结算方式说明，前后端展示同一文案。 */
    public static String ruleLabel(String zoneCode) {
        String normalized = normalize(zoneCode);
        return switch (normalized) {
            case REBUY -> "30%复购积分 + 70%现金";
            case CONSUME -> "商品积分购买";
            default -> "现金或余额购买";
        };
    }
}
