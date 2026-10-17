package com.macro.mall.portal;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Full-context tests must never fall back to the developer or live database. */
public abstract class IsolatedPortalMysqlTestSupport {
    @DynamicPropertySource
    static void isolatedServices(DynamicPropertyRegistry properties) {
        String url = System.getenv("MALL_MEMBERSHIP_TEST_JDBC_URL");
        if (url == null || !url.contains("127.0.0.1") || !url.contains("/mall_github_audit_")) {
            throw new IllegalStateException("Supply an isolated local mall_github_audit_* MySQL schema for full-context tests");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("MALL_MEMBERSHIP_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("MALL_MEMBERSHIP_TEST_DB_PASSWORD"));
        properties.add("spring.datasource.druid.initial-size", () -> 0);
        properties.add("spring.datasource.druid.min-idle", () -> 0);
        // These tests exercise JDBC and local context wiring, not a shared message broker.
        properties.add("spring.rabbitmq.dynamic", () -> false);
        properties.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
        properties.add("spring.rabbitmq.listener.direct.auto-startup", () -> false);
        properties.add("mall.dividend.schedule.enabled", () -> false);
    }
}
