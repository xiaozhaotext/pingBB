package com.macro.mall.portal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;

/**
 * 记录应用端 GET 访问，供后台“访问统计”展示。只写 sys_visit_log，任何失败都只记日志，绝不影响业务请求。
 * 控制器请求路径按前缀登记，静态资源、文档与探活接口不计入。
 */
public class VisitLogFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitLogFilter.class);

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public VisitLogFilter(JdbcTemplate jdbc, boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (enabled) {
                record(request);
            }
        }
    }

    private void record(HttpServletRequest request) {
        try {
            String path = request.getRequestURI();
            if (!"GET".equalsIgnoreCase(request.getMethod()) || !trackable(path)) {
                return;
            }
            Principal principal = request.getUserPrincipal();
            String ip = resolveClientIp(request);
            String visitor = principal != null && principal.getName() != null && !principal.getName().isBlank()
                    ? "u:" + principal.getName()
                    : "ip:" + ip;
            jdbc.update("INSERT INTO sys_visit_log(client,path,method,visitor_key,ip,user_agent,member_id)"
                            + " VALUES(?,?,?,?,?,?,?)",
                    "app", truncate(path, 200), "GET", truncate(visitor, 64), truncate(ip, 64),
                    truncate(request.getHeader("User-Agent"), 200), null);
        } catch (RuntimeException e) {
            LOGGER.debug("visit log skipped: {}", e.getMessage());
        }
    }

    private static boolean trackable(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String prefix : new String[] { "/actuator", "/druid", "/swagger-ui", "/v3/api-docs" }) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot < 0 || dot < slash;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        boolean loopback = "127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote);
        if (loopback) {
            String realIp = normalize(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = normalize(forwarded.split(",")[0]);
                if (first != null) {
                    return first;
                }
            }
        }
        return remote == null ? "" : remote;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
