package com.macro.mall.portal.component;

import com.macro.mall.common.membership.TriPoolService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Independent of the legacy daily-dividend rule and server default timezone. */
@Component
public class TriPoolDailyScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(TriPoolDailyScheduler.class);
    private final TriPoolService pools;
    @Value("${mall.tri-pool.schedule.enabled:true}") private boolean enabled;
    public TriPoolDailyScheduler(TriPoolService pools) { this.pools=pools; }

    @Scheduled(cron="0 0 0 * * *",zone="Asia/Shanghai")
    public void runDaily() {
        if (!enabled || !pools.enabled()) return;
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        try { LOG.info("三池每日分红完成 date={} results={}",date,pools.distributeDaily(date)); }
        catch (Exception error) { LOG.error("三池每日分红失败 date={}",date,error); }
    }
}
