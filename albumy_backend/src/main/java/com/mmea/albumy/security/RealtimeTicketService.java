package com.mmea.albumy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class RealtimeTicketService {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final byte[] secret;
    private final long ttlMs;

    public RealtimeTicketService(@Value("${jwt.secret}") String jwtSecret,
                                 @Value("${app.realtime.ticket-ttl-ms:86400000}") long ttlMs) {
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlMs = ttlMs;
    }

    /**
     * Produces an HMAC-signed, event-bound, expiring ticket. A ticket gives the same
     * realtime access as the 6-char guest code / full-album link it was issued together
     * with: subscribing to an event's live topics now requires either this ticket or a
     * matching organizer/admin JWT (see RealtimeAuthInterceptor).
     */
    public String issue(long eventId) {
        long expiry = System.currentTimeMillis() + ttlMs;
        return expiry + "." + hmac(eventId, expiry);
    }

    public boolean isValid(long eventId, String token) {
        if (token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expiry;
        try {
            expiry = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            return false;
        }
        String mac = hmac(eventId, expiry);
        String provided = token.substring(dot + 1);
        return MessageDigest.isEqual(
                mac.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(long eventId, long expiry) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return hex(mac.doFinal((eventId + ":" + expiry).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Realtime ticket signing failed", e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }
}