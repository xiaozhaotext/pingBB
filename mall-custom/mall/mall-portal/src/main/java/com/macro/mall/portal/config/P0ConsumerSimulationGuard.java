package com.macro.mall.portal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

/** Blocks consumer-created identity, points and simulated money events by default. */
@Component
public class P0ConsumerSimulationGuard implements WebMvcConfigurer {
    @Value("${mall.p0.consumer-simulation-enabled:false}")
    private boolean localSimulationEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String remote = request.getRemoteAddr();
                boolean loopback = "127.0.0.1".equals(remote) || "::1".equals(remote)
                        || "0:0:0:0:0:0:0:1".equals(remote);
                if (!localSimulationEnabled || !loopback) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "此消费者端模拟操作未开放，请使用有权限的后台接口");
                }
                return true;
            }
        }).addPathPatterns(
                "/member/identity/change",
                "/member/points/change", "/member/points/freeze", "/member/points/unfreeze",
                "/member/earnings/simulateReward", "/member/withdraw/process",
                "/member/dividend/contribution", "/member/dividend/allocate",
                "/member/dividend/snapshot", "/member/dividend/detail",
                "/member/partner/order", "/member/partner/pay",
                "/member/cofounder/order", "/member/cofounder/pay");
    }
}
