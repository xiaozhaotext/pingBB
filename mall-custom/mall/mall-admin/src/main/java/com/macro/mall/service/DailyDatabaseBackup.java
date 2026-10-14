package com.macro.mall.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨在后台配置的持久目录生成可从数据库管理页面下载的备份。 */
@Component
public class DailyDatabaseBackup {
    private static final Logger LOG = LoggerFactory.getLogger(DailyDatabaseBackup.class);
    private final SystemOpsService operations;

    public DailyDatabaseBackup(SystemOpsService operations) {
        this.operations = operations;
    }

    @Scheduled(cron = "${mall.database.backup-cron:0 0 2 * * *}",
               zone = "${mall.database.backup-zone:Asia/Shanghai}")
    public void run() {
        try {
            LOG.info("Daily database backup created: {}", operations.createBackup());
        } catch (RuntimeException error) {
            LOG.error("Daily database backup failed", error);
        }
    }
}
