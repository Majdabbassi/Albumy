package com.mmea.albumy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaFileTypesTest {

    @TempDir
    Path dir;

    private Path file(int... bytes) throws IOException {
        byte[] data = new byte[Math.max(bytes.length, 16)];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        return Files.write(dir.resolve("f" + System.nanoTime()), data);
    }

    // Regression: signatures starting with bytes >= 0x80 (PNG, JPEG) used to be compared as
    // negative ints and never matched, so every PNG/JPEG upload was rejected.
    @Test
    void sniffsPng() throws IOException {
        assertEquals("png", MediaFileTypes.sniffExtension(file(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
    }

    @Test
    void sniffsJpeg() throws IOException {
        assertEquals("jpg", MediaFileTypes.sniffExtension(file(0xFF, 0xD8, 0xFF, 0xE0)));
    }

    @Test
    void sniffsGifWebpWebm() throws IOException {
        assertEquals("gif", MediaFileTypes.sniffExtension(file('G', 'I', 'F', '8', '9', 'a')));
        assertEquals("webp", MediaFileTypes.sniffExtension(
                file('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
        assertEquals("webm", MediaFileTypes.sniffExtension(file(0x1A, 0x45, 0xDF, 0xA3)));
    }

    @Test
    void sniffsMp4HeicAndAvif() throws IOException {
        assertEquals("mp4", MediaFileTypes.sniffExtension(
                file(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm')));
        assertEquals("heic", MediaFileTypes.sniffExtension(
                file(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c')));
        assertEquals("avif", MediaFileTypes.sniffExtension(
                file(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f')));
    }

    @Test
    void rejectsUnknownAndTooShortContent() throws IOException {
        Path html = Files.write(dir.resolve("x.png"), "<html><script>1</script></html>".getBytes(StandardCharsets.UTF_8));
        assertNull(MediaFileTypes.sniffExtension(html));
        assertNull(MediaFileTypes.sniffExtension(Files.write(dir.resolve("tiny"), new byte[]{1, 2})));
    }

    @Test
    void contentMustMatchTheClaimedExtension() {
        assertTrue(MediaFileTypes.isCompatible("png", "png"));
        assertTrue(MediaFileTypes.isCompatible("jpg", "jpeg"));
        assertFalse(MediaFileTypes.isCompatible("png", "jpg"));
        assertFalse(MediaFileTypes.isCompatible(null, "png"));
    }

    @Test
    void extensionAllowlistBlocksActiveContent() {
        assertEquals("png", MediaFileTypes.storedExtension("holiday.PNG"));
        assertEquals("", MediaFileTypes.storedExtension("evil.svg"));
        assertEquals("", MediaFileTypes.storedExtension("evil.html"));
        assertEquals("", MediaFileTypes.storedExtension("noextension"));
        assertTrue(MediaFileTypes.isBlockedMime("image/svg+xml"));
        assertFalse(MediaFileTypes.isBlockedMime("image/png"));
    }
}
