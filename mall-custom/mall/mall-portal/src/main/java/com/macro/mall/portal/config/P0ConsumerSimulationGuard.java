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

/**
 * 【生产加固预案 - 当前未启用】
 * 作用：禁止消费者端自行产生积分/收益/身份/核验贡献。
 *
 * 之所以暂时取消 @Component 注册（而非删除本文件）：
 * 本类是「公众/internet 暴露」场景下的加固预案，一旦注册为 Bean，
 * 会对如下路径一律放行前要求 loopback 且开启开关，默认即 403：
 *   /member/identity/change、/member/points/*、/member/earnings/simulateReward、
 *   /member/withdraw/process、/member/dividend/*、/member/partner|{@code /}cofounder pay 等。
 * 这会导致：浏览器经 nginx(frontend容器) -> /portal -> backend 时，remoteAddr 是容器内网IP(如 172.x)，
 * 永远不满足 loopback 判断 -> 演示环境所有上述功能 403，本次交付演示直接不可用。
 *
 * 当前交付定位为「资金全模拟的内网演示」：所有涉及金额的表均带 simulate_flag=1，
 * 支付/分红/提现均未接真实渠道，符合客户要求的安全边界，故先不启用本加固。
 *
 * 【何时启用】后续若要把这些模拟接口对外网开放，需同时做三件事再注册 Bean：
 *   1) 把下面的 @Component 取消注释；
 *   2) 配置可信代理(server.forward-headers-strategy)并把 remoteAddr 判断改为读取可信 X-Forwarded-For；
 *   3) 补 mall.p0.consumer-simulation-enabled 的开关管理。
 * 未做完 1~3 就直接注册，会让前台功能整体不可用，请勿单独启用。
 */
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
