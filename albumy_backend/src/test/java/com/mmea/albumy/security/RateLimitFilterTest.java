package com.mmea.albumy.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private static final String BODY = "{\"username\":\"demo\",\"password\":\"secret123\"}";

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new RateLimiter());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "windowMinutes", 15);
        ReflectionTestUtils.setField(filter, "loginPerIp", 100);
        ReflectionTestUtils.setField(filter, "loginPerUsername", 2);
    }

    private MockHttpServletRequest login() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    // Regression: the filter read the body to find the username and then passed on an
    // already-drained stream, so the login controller always saw an empty body (HTTP 500).
    @Test
    void loginBodyIsStillReadableByTheNextHandler() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) ->
                seen.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        filter.doFilter(login(), new MockHttpServletResponse(), chain);

        assertEquals(BODY, seen.get());
    }

    @Test
    void loginIsThrottledPerUsername() throws Exception {
        FilterChain chain = (req, res) -> { };
        MockHttpServletResponse last = null;
        for (int i = 0; i < 3; i++) {
            last = new MockHttpServletResponse();
            filter.doFilter(login(), last, chain);
        }
        assertEquals(429, last.getStatus());
    }

    @Test
    void oversizedLoginBodyIsRejected() throws Exception {
        MockHttpServletRequest request = login();
        request.setContent(new byte[20_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertEquals(413, response.getStatus());
        assertFalse(called.get());
    }
}
