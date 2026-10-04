package com.mmea.albumy.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigTest {

    private CorsConfiguration config() {
        SecurityConfig security = new SecurityConfig(null, null, "https://app.example, http://localhost:4200");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events/code/ABC/photos");
        return security.corsConfigurationSource().getCorsConfiguration(request);
    }

    // Regression: X-Guest-Token was missing from the allowed headers, so every guest request
    // from another origin (the GitHub Pages demo, the Android app) failed its preflight.
    @Test
    void everyHeaderTheFrontendSendsIsAllowed() {
        List<String> requested = List.of("Authorization", "Content-Type", "X-Guest-Token", "X-Realtime-Token");
        // checkHeaders returns only the allowed subset, so all of them must come back.
        assertEquals(requested, config().checkHeaders(requested));
    }

    @Test
    void onlyConfiguredOriginsAreAllowed() {
        assertEquals("https://app.example", config().checkOrigin("https://app.example"));
        assertNull(config().checkOrigin("https://evil.example"));
    }
}
