package com.mmea.albumy.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Central policy for which files are allowed onto the upload surface. */
public final class MediaFileTypes {

    private MediaFileTypes() {
    }

    /** Stored extensions that are safe to serve inline. SVGs/HTML are intentionally excluded. */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif",
            "mp4", "m4v", "mov", "webm");

    /** MIME types that must never be accepted, even if the extension looks benign. */
    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "image/svg+xml", "image/svg", "text/html", "application/xhtml+xml",
            "application/xml", "text/xml", "application/javascript", "text/javascript",
            "text/plain", "application/json");

    public static boolean isAllowedExtension(String extension) {
        return extension != null && !extension.isBlank() && ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }

    public static boolean isBlockedMime(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String mime = mimeType.toLowerCase();
        for (String blocked : BLOCKED_MIME_TYPES) {
            if (mime.equals(blocked) || mime.startsWith(blocked + ";")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves to the stored extension for a client-supplied file name, or an empty
     * string when the file type is not on the allowlist. An empty result means
     * the upload must be rejected.
     */
    public static String storedExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return ext.matches("[a-z0-9]{1,10}") && isAllowedExtension(ext) ? ext : "";
    }

    /** Sniffs the magic bytes of a file and returns a detected extension, or null when unrecognized. */
    public static String sniffExtension(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = new byte[12];
            int read = in.read(head);
            if (read < 4) {
                return null;
            }
            if (startsWith(head, read, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
                return "jpg";
            }
            if (startsWith(head, read, (byte) 0x89, 0x50, 0x4E, 0x47)) {
                return "png";
            }
            if (startsWith(head, read, (byte) 0x47, 0x49, 0x46, 0x38)) {
                return "gif";
            }
            if (startsWith(head, read, 0x52, 0x49, 0x46, 0x46) && read >= 12
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                return "webp";
            }
            if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
                if (read >= 12) {
                    String brand = new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).toLowerCase();
                    if (brand.startsWith("he") || brand.equals("mif1") || brand.equals("msf1")) {
                        return "heic";
                    }
                    if (brand.equals("avif") || brand.equals("avis")) {
                        return "avif";
                    }
                }
                return "mp4";
            }
            if (startsWith(head, read, (byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3)) {
                return "webm";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Whether a sniffed type may be stored under the given (already allowlisted) extension. */
    public static boolean isCompatible(String sniffed, String extension) {
        if (sniffed == null || extension == null) {
            return false;
        }
        String ext = extension.toLowerCase();
        return switch (sniffed) {
            case "jpg" -> ext.equals("jpg") || ext.equals("jpeg");
            case "mp4" -> ext.equals("mp4") || ext.equals("m4v") || ext.equals("mov");
            case "heic" -> ext.equals("heic") || ext.equals("heif");
            case "png", "gif", "webp", "webm", "avif" -> ext.equals(sniffed);
            default -> false;
        };
    }

    /** Canonical MIME for a stored file name, or null when the extension isn't known. */
    public static String mimeFor(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return switch (fileName.substring(dot + 1).toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic", "heif" -> "image/heic";
            case "avif" -> "image/avif";
            case "mp4", "m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            default -> null;
        };
    }

    private static boolean startsWith(byte[] head, int len, int... expected) {
        if (head.length < len || len < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != (expected[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}