package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.dto.UploadInitRequest;
import com.mmea.albumy.dto.UploadInitResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.service.MediaQueueService;
import com.mmea.albumy.service.RealtimeEventsService;
import com.mmea.albumy.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);

    private final StringRedisTemplate redis;
    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final PhotoRepository photoRepository;
    private final MediaQueueService mediaQueueService;
    private final RealtimeEventsService realtimeEventsService;
    private final String uploadDir;
    private final int chunkSize;
    private final long maxFile;
    private final Duration sessionTtl;

    public UploadServiceImpl(StringRedisTemplate redis,
                             EventRepository eventRepository,
                             GuestRepository guestRepository,
                             PhotoRepository photoRepository,
                             MediaQueueService mediaQueueService,
                             RealtimeEventsService realtimeEventsService,
                             @Value("${upload.dir:uploads}") String uploadDir,
                             @Value("${upload.chunk.size:5242880}") int chunkSize,
                             @Value("${upload.max.file:2147483648}") long maxFile,
                             @Value("${upload.session.ttl.hours:24}") int sessionTtlHours) {
        this.redis = redis;
        this.eventRepository = eventRepository;
        this.guestRepository = guestRepository;
        this.photoRepository = photoRepository;
        this.mediaQueueService = mediaQueueService;
        this.realtimeEventsService = realtimeEventsService;
        this.uploadDir = uploadDir;
        this.chunkSize = chunkSize;
        this.maxFile = maxFile;
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Override
    public UploadInitResponse init(UploadInitRequest request) {
        if (request.getEventCode() == null || request.getGuestToken() == null || request.getGuestToken().isBlank()) {
            throw ApiException.unauthorized("Guest token required");
        }
        Event event = eventRepository.findByEventCode(request.getEventCode())
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        guestRepository.findByEventAndGuestToken(event, request.getGuestToken())
                .orElseThrow(() -> ApiException.unauthorized("Invalid guest token"));

        if (request.getTotalChunks() < 1
                || request.getTotalChunks() > Math.ceil((double) maxFile / chunkSize)) {
            throw ApiException.badRequest("Invalid chunk count");
        }
        if (request.getFileName() == null || request.getFileName().isBlank() || request.getFileName().length() > 255) {
            throw ApiException.badRequest("A file name is required");
        }

        String uploadId = UUID.randomUUID().toString();
        redis.opsForHash().putAll("upload:" + uploadId, Map.of(
                "eventCode", request.getEventCode(),
                "guestToken", request.getGuestToken(),
                "fileName", request.getFileName(),
                "mimeType", request.getMimeType() == null ? "" : request.getMimeType(),
                "totalChunks", String.valueOf(request.getTotalChunks()),
                "size", String.valueOf(request.getSize())
        ));
        redis.expire("upload:" + uploadId, sessionTtl);

        return new UploadInitResponse(uploadId, chunkSize);
    }

    @Override
    public void storeChunk(String uploadId, int chunkIndex, byte[] data) {
        Map<Object, Object> session = session(uploadId);
        int totalChunks = Integer.parseInt((String) session.get("totalChunks"));
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw ApiException.badRequest("Chunk index out of range");
        }
        if (data.length > chunkSize) {
            throw ApiException.badRequest("Chunk exceeds the " + chunkSize + " byte limit");
        }

        try {
            Path chunkDir = chunkDir(uploadId);
            Files.createDirectories(chunkDir);
            Files.write(chunkDir.resolve(String.valueOf(chunkIndex)), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store chunk", e);
        }
        redis.opsForSet().add("upload:" + uploadId + ":chunks", String.valueOf(chunkIndex));
        redis.expire("upload:" + uploadId + ":chunks", sessionTtl);
    }

    @Override
    public PhotoResponse complete(String uploadId) {
        Map<Object, Object> session = session(uploadId);
        String eventCode = (String) session.get("eventCode");
        String guestToken = (String) session.get("guestToken");
        String fileName = (String) session.get("fileName");
        String mimeType = (String) session.get("mimeType");
        int totalChunks = Integer.parseInt((String) session.get("totalChunks"));

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        Guest guest = guestRepository.findByEventAndGuestToken(event, guestToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid guest token"));

        Set<String> received = redis.opsForSet().members("upload:" + uploadId + ":chunks");
        if (received == null || received.size() < totalChunks) {
            throw ApiException.badRequest("Not all chunks have been uploaded yet");
        }

        String sha256;
        Path assembled;
        try {
            assembled = assemble(uploadId, totalChunks);
            sha256 = sha256Of(assembled);
        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble upload", e);
        }

        Photo existing = photoRepository.findFirstByEventAndSha256(event, sha256).orElse(null);
        if (existing != null) {
            cleanup(uploadId);
            return PhotoResponse.from(existing);
        }

        String extension = extensionOf(fileName);
        String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        try {
            Path target = Paths.get(uploadDir, storedName).toAbsolutePath();
            Files.createDirectories(target.getParent());
            Files.move(assembled, target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store assembled file", e);
        }

        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setGuest(guest);
        photo.setFileName(storedName);
        photo.setOriginalName(fileName);
        photo.setMimeType(mimeType == null || mimeType.isEmpty() ? guessMime(storedName) : mimeType);
        photo.setSize(Files.exists(Paths.get(uploadDir, storedName))
                ? safeSize(Paths.get(uploadDir, storedName)) : 0L);
        photo.setSha256(sha256);
        photo.setStatus(PhotoStatus.PROCESSING);
        Photo saved = photoRepository.save(photo);

        mediaQueueService.enqueue(saved.getId());
        realtimeEventsService.photoAdded(saved);
        cleanup(uploadId);
        log.info("Chunked upload complete: photo {} ({}) in event {}", saved.getId(), storedName, eventCode);
        return PhotoResponse.from(saved);
    }

    public List<Integer> receivedChunks(String uploadId) {
        session(uploadId); // validates existence
        Set<String> members = redis.opsForSet().members("upload:" + uploadId + ":chunks");
        if (members == null) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>(members.size());
        for (String m : members) {
            indices.add(Integer.parseInt(m));
        }
        indices.sort(Integer::compareTo);
        return indices;
    }

    private Map<Object, Object> session(String uploadId) {
        Map<Object, Object> session = redis.opsForHash().entries("upload:" + uploadId);
        if (session.isEmpty() || session.get("eventCode") == null) {
            throw ApiException.notFound("Upload session not found or expired");
        }
        return session;
    }

    private Path chunkDir(String uploadId) {
        return Paths.get(uploadDir, "tmp", uploadId).toAbsolutePath();
    }

    private Path assemble(String uploadId, int totalChunks) throws IOException {
        Path chunkDir = chunkDir(uploadId);
        Path assembled = Paths.get(uploadDir, "tmp", uploadId + ".bin").toAbsolutePath();
        Files.createDirectories(assembled.getParent());

        try (OutputStream out = Files.newOutputStream(assembled)) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunk = chunkDir.resolve(String.valueOf(i));
                if (!Files.exists(chunk)) {
                    throw new IOException("Missing chunk " + i);
                }
                Files.copy(chunk, out);
            }
        }
        return assembled;
    }

    private String sha256Of(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash uploaded file", e);
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return ext.matches("[a-z0-9]{1,8}") ? ext : "";
    }

    private String guessMime(String fileName) {
        String ext = extensionOf(fileName);
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic", "heif" -> "image/heic";
            case "mp4", "m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private void cleanup(String uploadId) {
        try {
            Path tmp = Paths.get(uploadDir, "tmp");
            Files.deleteIfExists(tmp.resolve(uploadId + ".bin"));
            Path chunks = tmp.resolve(uploadId);
            if (Files.isDirectory(chunks)) {
                try (var stream = Files.list(chunks)) {
                    stream.forEach(c -> {
                        try {
                            Files.deleteIfExists(c);
                        } catch (IOException ignored) {
                        }
                    });
                }
                Files.deleteIfExists(chunks);
            }
        } catch (IOException ignored) {
        }
        redis.delete("upload:" + uploadId);
        redis.delete("upload:" + uploadId + ":chunks");
    }
}