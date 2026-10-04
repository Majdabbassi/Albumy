package com.mmea.albumy.dto;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhotoResponseTest {

    // Regression: uploadedAt was a zone-less LocalDateTime, so a browser in UTC+1 showed
    // "1 hr ago" for a photo uploaded seconds earlier. It is now an absolute instant.
    @Test
    void uploadedAtIsAnAbsoluteInstant() {
        LocalDateTime local = LocalDateTime.of(2026, 10, 4, 19, 2, 33);
        Instant instant = (Instant) ReflectionTestUtils.invokeMethod(PhotoResponse.class, "toInstant", local);

        assertEquals(local.atZone(ZoneId.systemDefault()).toInstant(), instant);
        assertNull(ReflectionTestUtils.invokeMethod(PhotoResponse.class, "toInstant", (Object) null));
    }
}
