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
import com.mmea.albumy.util.MediaFileTypes;
import com.mmea.albumy.util.PhotoFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
@EnableScheduling
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
    private final Map<String, Object> uploadLocks = new ConcurrentHashMap<>();

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
        if (request.getSize() <= 0 || request.getSize() > maxFile) {
            throw ApiException.badRequest("Invalid file size");
        }
        if (MediaFileTypes.storedExtension(request.getFileName()).isEmpty()) {
            throw ApiException.badRequest("Unsupported file type. Only images and videos are allowed.");
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
        Object lock = uploadLocks.computeIfAbsent(uploadId, k -> new Object());
        synchronized (lock) {
            return doComplete(uploadId);
        }
    }

    private PhotoResponse doComplete(String uploadId) {
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

        String extension = MediaFileTypes.storedExtension(fileName);
        if (extension.isEmpty()) {
            cleanup(uploadId);
            throw ApiException.badRequest("Unsupported file type. Only images and videos are allowed.");
        }

        String sha256;
        Path assembled;
        try {
            assembled = assemble(uploadId, totalChunks);
            long assembledSize = safeSize(assembled);
            if (assembledSize > maxFile) {
                cleanup(uploadId);
                throw ApiException.badRequest("File exceeds the maximum upload size");
            }
            if (session.get("size") != null && !((String) session.get("size")).isBlank()) {
                long declaredSize;
                try {
                    declaredSize = Long.parseLong((String) session.get("size"));
                } catch (NumberFormatException e) {
                    cleanup(uploadId);
                    throw ApiException.badRequest("Invalid declared size");
                }
                if (declaredSize != assembledSize) {
                    cleanup(uploadId);
                    throw ApiException.badRequest("Uploaded size does not match the declared size");
                }
            }
            sha256 = sha256Of(assembled);
        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble upload", e);
        }

        String sniffed = MediaFileTypes.sniffExtension(assembled);
        if (sniffed == null || !MediaFileTypes.isCompatible(sniffed, extension)) {
            cleanup(uploadId);
            throw ApiException.badRequest("File content does not match its extension");
        }

        Photo existing = photoRepository.findFirstByEventAndSha256(event, sha256).orElse(null);
        if (existing != null) {
            cleanup(uploadId);
            return PhotoResponse.from(existing);
        }

        String storedName = UUID.randomUUID() + "." + extension;

        try {
            Path target = Paths.get(uploadDir, storedName).toAbsolutePath();
            Files.createDirectories(target.getParent());
            Files.move(assembled, target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store assembled file", e);
        }

        String resolvedMime = (mimeType == null || mimeType.isEmpty() || MediaFileTypes.isBlockedMime(mimeType))
                ? guessMime(storedName)
                : mimeType;

        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setGuest(guest);
        photo.setFileName(storedName);
        photo.setOriginalName(fileName);
        photo.setMimeType(resolvedMime);
        photo.setSize(Files.exists(Paths.get(uploadDir, storedName))
                ? safeSize(Paths.get(uploadDir, storedName)) : 0L);
        photo.setSha256(sha256);
        photo.setStatus(PhotoStatus.PROCESSING);
        Photo saved;
        try {
            saved = photoRepository.saveAndFlush(photo);
        } catch (DataIntegrityViolationException e) {
            PhotoFiles.deleteFileIfExists(uploadDir, storedName);
            existing = photoRepository.findFirstByEventAndSha256(event, sha256).orElse(null);
            cleanup(uploadId);
            if (existing != null) {
                return PhotoResponse.from(existing);
            }
            throw e;
        }

        mediaQueueService.enqueue(saved.getId(), resolvedMime);
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

    private String guessMime(String fileName) {
        String mime = MediaFileTypes.mimeFor(fileName);
        return mime != null ? mime : "application/octet-stream";
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

    /**
     * Periodic sweep that reclaims on-disk chunk/tmp files for upload sessions
     * that expired or were abandoned (paused/offline) past the session TTL.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void sweepStaleTmp() {
        Path tmp = Paths.get(uploadDir, "tmp").toAbsolutePath();
        if (!Files.isDirectory(tmp)) {
            return;
        }
        long ttlMillis = sessionTtl.toMillis();
        try (Stream<Path> entries = Files.list(tmp)) {
            entries.forEach(entry -> {
                long ageMillis;
                try {
                    ageMillis = System.currentTimeMillis() - Files.getLastModifiedTime(entry).toMillis();
                } catch (IOException e) {
                    return;
                }
                if (ageMillis >= ttlMillis) {
                    deleteRecursively(entry);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to sweep upload tmp directory", e);
        }
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}