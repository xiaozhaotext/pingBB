package com.macro.mall.portal.component;

import com.macro.mall.portal.service.DividendDistributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日分红发放定时任务。
 *
 * 说明：daily 分红此前只有实现、没有任何触发入口（死代码），导致「每日分红」永远不产生收益。
 * 这里按日结算：默认每天 01:10 跑一次，结算「昨天」的分红；
 * 是否真正发放仍由后台配置 dividend.distribution.enabled 控制，关闭时直接跳过。
 */
@Component
public class DividendDailyScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DividendDailyScheduler.class);

    private final DividendDistributionService distributionService;

    @Value("${mall.dividend.schedule.enabled:true}")
    private boolean scheduleEnabled;

    public DividendDailyScheduler(DividendDistributionService distributionService) {
        this.distributionService = distributionService;
    }

    @Scheduled(cron = "${mall.dividend.schedule.cron:0 10 1 * * ?}")
    public void runDaily() {
        if (!scheduleEnabled) return;
        LocalDate statDate = LocalDate.now().minusDays(1);
        try {
            int paid = distributionService.distributeDaily(statDate);
            LOGGER.info("每日分红结算完成 statDate={} 发放人数={}", statDate, paid);
        } catch (Exception ex) {
            // 规则关闭或池余额为空属于正常跳过，其余异常需要可观测
            LOGGER.warn("每日分红结算跳过/失败 statDate={} 原因={}", statDate, ex.getMessage());
        }
    }
}
