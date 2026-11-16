package com.macro.mall.portal.component;

import com.macro.mall.common.membership.TriPoolService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 独立于旧版日分红任务，每周一按北京时间结算上一自然周。 */
@Component
public class TriPoolWeeklyScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(TriPoolWeeklyScheduler.class);
    private final TriPoolService pools;
    @Value("${mall.tri-pool.schedule.enabled:true}") private boolean enabled;
    public TriPoolWeeklyScheduler(TriPoolService pools) { this.pools=pools; }

    @Scheduled(cron="0 0 0 * * MON",zone="Asia/Shanghai")
    public void runWeekly() {
        if (!enabled || !pools.enabled()) return;
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).with(java.time.temporal.TemporalAdjusters.previous(java.time.DayOfWeek.SUNDAY));
        try { LOG.info("三池每周分红完成 date={} results={}",date,pools.distributeWeekly(date)); }
        catch (Exception error) { LOG.error("三池每周分红失败 date={}",date,error); }
    }
}
