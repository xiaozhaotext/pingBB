package com.macro.mall.config;

import com.macro.mall.service.SystemSettingService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers the IP access filter ahead of the Spring Security filter chain. */
@Configuration
public class AdminIpAccessConfig {

    @Bean
    public FilterRegistrationBean<AdminIpAccessFilter> adminIpAccessFilterRegistration(SystemSettingService settings) {
        FilterRegistrationBean<AdminIpAccessFilter> registration =
                new FilterRegistrationBean<>(new AdminIpAccessFilter(settings));
        registration.addUrlPatterns("/*");
        registration.setName("adminIpAccessFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
