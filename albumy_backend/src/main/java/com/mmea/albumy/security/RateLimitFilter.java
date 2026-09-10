package com.mmea.albumy.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.window-minutes:15}")
    private int windowMinutes;

    @Value("${app.rate-limit.login-per-ip:20}")
    private int loginPerIp;

    @Value("${app.rate-limit.login-per-username:10}")
    private int loginPerUsername;

    @Value("${app.rate-limit.register-per-ip:20}")
    private int registerPerIp;

    @Value("${app.rate-limit.event-info-per-ip:200}")
    private int eventInfoPerIp;

    @Value("${app.rate-limit.name-available-per-ip:60}")
    private int nameAvailablePerIp;

    @Value("${app.rate-limit.claim-per-ip:30}")
    private int claimPerIp;

    @Value("${app.rate-limit.upload-init-per-ip:100}")
    private int uploadInitPerIp;

    @Value("${app.rate-limit.upload-complete-per-ip:150}")
    private int uploadCompletePerIp;

    private static final String PATH_LOGIN = "/auth/login";
    private static final String PATH_REGISTER = "/auth/register";
    private static final String PATH_EVENT_INFO = "/events/code/*";
    private static final String PATH_NAME_AVAILABLE = "/events/code/*/name-available";
    private static final String PATH_CLAIM = "/events/code/*/claim";
    private static final String PATH_UPLOAD_INIT = "/uploads";
    private static final String PATH_UPLOAD_COMPLETE = "/uploads/*/complete";

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        if (enabled) {
            String ip = clientIp(request);
            Duration window = Duration.ofMinutes(windowMinutes);

            if (isLogin(method, path)) {
                ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, 16384);
                wrapper.getInputStream().readAllBytes();
                boolean ipAllowed = rateLimiter.allow("login:ip:" + ip, loginPerIp, window);
                boolean userAllowed = !ipAllowed || userLoginAllowed(wrapper);
                if (!userAllowed) {
                    deny(response);
                    return;
                }
                filterChain.doFilter(wrapper, response);
                return;
            }
            if (match("POST", method, path, PATH_REGISTER)
                    && !rateLimiter.allow("register:ip:" + ip, registerPerIp, window)) {
                deny(response);
                return;
            }
            if (match("GET", method, path, PATH_NAME_AVAILABLE)
                    && !rateLimiter.allow("name:ip:" + ip, nameAvailablePerIp, window)) {
                deny(response);
                return;
            }
            if (match("POST", method, path, PATH_CLAIM)
                    && !rateLimiter.allow("claim:ip:" + ip, claimPerIp, window)) {
                deny(response);
                return;
            }
            if (match("GET", method, path, PATH_EVENT_INFO)
                    && !rateLimiter.allow("event-info:ip:" + ip, eventInfoPerIp, window)) {
                deny(response);
                return;
            }
            if (match("POST", method, path, PATH_UPLOAD_INIT)
                    && !rateLimiter.allow("upload-init:ip:" + ip, uploadInitPerIp, window)) {
                deny(response);
                return;
            }
            if (match("POST", method, path, PATH_UPLOAD_COMPLETE)
                    && !rateLimiter.allow("upload-complete:ip:" + ip, uploadCompletePerIp, window)) {
                deny(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLogin(String method, String path) {
        return "POST".equals(method) && PATH_LOGIN.equals(path);
    }

    private boolean match(String expectedMethod, String method, String path, String pattern) {
        return expectedMethod.equals(method) && pathMatcher.match(pattern, path);
    }

    private boolean userLoginAllowed(ContentCachingRequestWrapper wrapper) {
        String username = extractUsername(wrapper);
        if (username == null) {
            return true;
        }
        return rateLimiter.allow("login:user:" + username.toLowerCase(Locale.ROOT),
                loginPerUsername, Duration.ofMinutes(windowMinutes));
    }

    private String extractUsername(ContentCachingRequestWrapper wrapper) {
        byte[] body = wrapper.getContentAsByteArray();
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode username = node.get("username");
            return username == null || username.isNull() ? null : username.asText();
        } catch (IOException e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", "Too many requests. Try again later.")));
    }
}