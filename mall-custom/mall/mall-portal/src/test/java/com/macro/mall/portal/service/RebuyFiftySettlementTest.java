package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.ZoneRules;
import com.macro.mall.portal.domain.ZoneSettlement;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** 使用独立内存配置验证默认比例、实际配置及混合订单资金守恒。 */
class RebuyFiftySettlementTest {
    private ZoneSettlementService service(String ratio) {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:rebuy_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_config(config_key VARCHAR(100),config_value VARCHAR(100))");
        if (ratio != null) jdbc.update("INSERT INTO sys_config VALUES('rebuy.points.ratio',?)", ratio);
        return new ZoneSettlementService(jdbc);
    }
    private ZoneSettlementService.Line line(String zone, String price) {
        return new ZoneSettlementService.Line(zone, new BigDecimal(price), 1, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    private void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
    @Test void defaultAndConfiguredRatioBothDeductFiftyPercent() {
        for (String ratio : new String[]{null, "0.50"}) {
            var result = service(ratio).settle(List.of(line(ZoneRules.REBUY, "100")), true, false,
                    new ZoneSettlementService.Wallets(0, 50, BigDecimal.ZERO));
            money("50", result.getRepurchasePointAmount());
            money("50", result.getCashAmount());
            assertTrue(result.getPlans().get(0).isPayable());
        }
    }
    @Test void fortyNinePointsCannotPayFiftyPointsButCashChoiceStillWorks() {
        var service = service("0.50");
        var lines = List.of(line(ZoneRules.REBUY, "100"));
        var wallets = new ZoneSettlementService.Wallets(9999, 49, BigDecimal.ZERO);
        assertFalse(service.settle(lines, true, false, wallets).getPlans().get(0).isPayable());
        var cash = service.settle(lines, false, false, wallets);
        money("100", cash.getCashAmount());
        money("0", cash.getRepurchasePointAmount());
    }
    @Test void mixedCartUsesBalanceOnceAndPreservesCurrentConsumeCashRule() {
        var result = service("0.50").settle(List.of(line(ZoneRules.PICK, "80"),
                line(ZoneRules.REBUY, "100"), line(ZoneRules.CONSUME, "30")), true, true,
                new ZoneSettlementService.Wallets(9999, 50, new BigDecimal("100")));
        money("210", result.getTotalAmount());
        money("100", result.getBalanceAmount());
        money("50", result.getRepurchasePointAmount());
        money("60", result.getCashAmount());
        money("0", result.getProductPointAmount());
    }
    @Test void fractionalPointsRemainCashAndWholePointBoundaryIsTwoYuan() {
        var service = service("0.50");
        var wallets = new ZoneSettlementService.Wallets(0, 0, BigDecimal.ZERO);
        var below = service.settle(List.of(line(ZoneRules.REBUY, "1.99")), true, false, wallets);
        assertTrue(below.getPlans().get(0).isPayable());
        money("1.99", below.getCashAmount());
        assertFalse(service.settle(List.of(line(ZoneRules.REBUY, "2.00")), true, false, wallets)
                .getPlans().get(0).isPayable());
    }
}
