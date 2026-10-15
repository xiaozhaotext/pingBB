package com.macro.mall.portal.component;

import com.macro.mall.portal.service.RechargeService;
import com.macro.mall.portal.config.RechargeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RechargeReconciliation {
    private static final Logger log=LoggerFactory.getLogger(RechargeReconciliation.class);
    private final JdbcTemplate jdbc;
    private final RechargeService service;
    private final RechargeProperties properties;
    public RechargeReconciliation(JdbcTemplate jdbc,RechargeService service,RechargeProperties properties) {this.jdbc=jdbc;this.service=service;this.properties=properties;}
    @Scheduled(fixedDelayString="${mall.recharge.reconcile-delay-ms:60000}",initialDelay=60000)
    public void reconcile() {
        if(!properties.getPayment().isEnabled()) return;
        try {
            var cutoff=java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(30));
            for (var row:jdbc.queryForList("SELECT order_no,channel FROM ums_recharge_order WHERE status='PENDING' AND created_at<? ORDER BY checked_at ASC,created_at ASC LIMIT 100",cutoff)) {
                String no=(String)row.get("order_no");
                try {
                    var payment=service.gateway().query(no,(String)row.get("channel"));
                    if(payment!=null) service.accept(payment);
                } catch(Exception ex) {log.warn("Recharge reconciliation failed for order {} ({})",no,ex.getClass().getSimpleName());}
                finally {jdbc.update("UPDATE ums_recharge_order SET checked_at=CURRENT_TIMESTAMP WHERE order_no=?",no);}
            }
        } catch(Exception ex) {log.error("Recharge reconciliation unavailable ({})",ex.getClass().getSimpleName());}
    }
}
