package com.mmea.albumy.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the JWT signing key is missing, too short, or still the publicly
 * shipped placeholder. The repo is public, so a deployed instance that runs with the
 * placeholder would let anyone mint tokens for e.g. {@code demo_admin}. Loading the
 * context aborts (throw from the constructor) instead of booting insecurely.
 */
@Component
public class JwtSecretGuard {

    public static final String PLACEHOLDER = "albumy-dev-only-secret-change-me-in-production";

    private static final Logger log = LoggerFactory.getLogger(JwtSecretGuard.class);

    public JwtSecretGuard(@Value("${jwt.secret}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "Refusing to start: jwt.secret is not set. Generate one with "
                            + "`openssl rand -hex 32` and expose it as JWT_SECRET (see .env.example).");
        }
        if (PLACEHOLDER.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Refusing to start with the default/dev JWT secret. This repository is public, "
                            + "so the shipped placeholder is known to everyone and a deployed instance "
                            + "would let anyone forge admin tokens. Set JWT_SECRET (e.g. `openssl rand -hex 32`).");
        }
        if (jwtSecret.getBytes().length < 32) {
            throw new IllegalStateException("Refusing to start: jwt.secret must be at least 32 bytes for HS256.");
        }
        log.info("JWT secret validated ({} bytes)", jwtSecret.getBytes().length);
    }
}