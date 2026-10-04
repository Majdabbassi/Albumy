package com.mmea.albumy.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_LOGIN_BODY = 16384;

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
                byte[] body = request.getInputStream().readNBytes(MAX_LOGIN_BODY + 1);
                if (body.length > MAX_LOGIN_BODY) {
                    response.setStatus(413);
                    return;
                }
                boolean ipAllowed = rateLimiter.allow("login:ip:" + ip, loginPerIp, window);
                boolean userAllowed = !ipAllowed || userLoginAllowed(body);
                if (!userAllowed) {
                    deny(response);
                    return;
                }
                // The body was consumed above, so hand the controller a replayable copy.
                filterChain.doFilter(new ReplayableRequest(request, body), response);
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

    private boolean userLoginAllowed(byte[] body) {
        String username = extractUsername(body);
        if (username == null) {
            return true;
        }
        return rateLimiter.allow("login:user:" + username.toLowerCase(Locale.ROOT),
                loginPerUsername, Duration.ofMinutes(windowMinutes));
    }

    private String extractUsername(byte[] body) {
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

    /** Serves an already-read request body again so downstream code can still parse it. */
    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }
    }
}