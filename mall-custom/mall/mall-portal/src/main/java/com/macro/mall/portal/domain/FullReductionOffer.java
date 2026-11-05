package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** A same-product, same-zone threshold calculated from catalogue prices before wallet deductions. */
@Getter
@Setter
public class FullReductionOffer {
    private Long productId;
    private String productName;
    private String zoneCode;
    private BigDecimal currentAmount;
    private BigDecimal threshold;
    private BigDecimal discount;
    private BigDecimal gap;
}
