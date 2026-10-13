package com.macro.mall.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

/** 注册应用端访问统计过滤器，在所有安全过滤器之后执行，确保能拿到已认证的会员身份。 */
@Configuration
public class VisitLogConfig {

    @Bean
    public FilterRegistrationBean<VisitLogFilter> visitLogFilterRegistration(
            JdbcTemplate jdbcTemplate, @Value("${mall.visit-log.enabled:true}") boolean enabled) {
        FilterRegistrationBean<VisitLogFilter> registration =
                new FilterRegistrationBean<>(new VisitLogFilter(jdbcTemplate, enabled));
        registration.addUrlPatterns("/*");
        registration.setName("visitLogFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
