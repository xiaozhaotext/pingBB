package com.macro.mall.config;

import com.macro.mall.service.SystemSettingService;
import com.macro.mall.service.SystemSettingService.IpAccessRule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests whose client IP does not satisfy the ip.access.* settings. Runs before Spring
 * Security so a blocked address never reaches the admin API at all.
 *
 * The setting is re-read at most every 10 seconds, so a lock-out can be undone from the database
 * without restarting the service. Loopback is always allowed as a lock-out safety net.
 */
public class AdminIpAccessFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminIpAccessFilter.class);
    private static final long CACHE_MILLIS = 10_000L;

    private final SystemSettingService settings;
    private volatile IpAccessRule cachedRule;
    private volatile long cachedAt;

    public AdminIpAccessFilter(SystemSettingService settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        IpAccessRule rule;
        try {
            rule = currentRule();
        } catch (RuntimeException e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":503,\"message\":\"后台访问策略暂不可用，请稍后重试\",\"data\":null}");
            return;
        }
        if (rule.enabled()) {
            String clientIp = resolveClientIp(request);
            if (!rule.allows(clientIp)) {
                LOGGER.warn("blocked {} {} from {}", request.getMethod(), request.getRequestURI(), clientIp);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":403,\"message\":\"当前 IP 不在后台访问允许范围内\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private IpAccessRule currentRule() {
        long now = System.currentTimeMillis();
        IpAccessRule rule = cachedRule;
        if (rule == null || now - cachedAt > CACHE_MILLIS) {
            try {
                rule = settings.ipAccessRule();
                cachedRule = rule;
                cachedAt = now;
            } catch (RuntimeException e) {
                LOGGER.warn("ip access settings unavailable, keeping previous rule: {}", e.getMessage());
                if (rule == null) {
                    throw e;
                }
            }
        }
        return rule;
    }

    /**
     * Resolves the caller address. Forwarded headers are honoured only when the direct peer is
     * loopback, which is the case for the co-located Nginx reverse proxy; other peers cannot spoof
     * their address. Both services bind to 127.0.0.1, so this is the deployment's normal path.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (IpAccessRule.isLoopback(remote)) {
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
        if (trimmed.isEmpty() || trimmed.length() > 45) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '%';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}
