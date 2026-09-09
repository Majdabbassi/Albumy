package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.GuestClaimResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.service.GuestService;
import com.mmea.albumy.service.MediaQueueService;
import com.mmea.albumy.service.RealtimeEventsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GuestServiceImpl implements GuestService {

    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final MediaQueueService mediaQueueService;
    private final RealtimeEventsService realtimeEventsService;
    private final String uploadDir;

    public GuestServiceImpl(EventRepository eventRepository, GuestRepository guestRepository,
                            PhotoRepository photoRepository, UserRepository userRepository,
                            MediaQueueService mediaQueueService,
                            RealtimeEventsService realtimeEventsService,
                            @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.guestRepository = guestRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.mediaQueueService = mediaQueueService;
        this.realtimeEventsService = realtimeEventsService;
        this.uploadDir = uploadDir;
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @Override
    public EventPublicInfo getEventPublicInfo(String eventCode) {
        Event event = findByCodeOrThrow(eventCode);
        return new EventPublicInfo(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getCoverFileName() == null ? null : "/files/" + event.getCoverFileName()
        );
    }

    @Override
    public Boolean isNameAvailable(String eventCode, String name) {
        Event event = findByCodeOrThrow(eventCode);
        if (name == null || name.trim().length() < 2) {
            return false;
        }
        return !guestRepository.existsByEventAndName(event, name.trim());
    }

    @Override
    public GuestClaimResponse claimGuest(String eventCode, String name) {
        Event event = findByCodeOrThrow(eventCode);

        if (name == null || name.trim().length() < 2) {
            throw ApiException.badRequest("A display name of at least 2 characters is required");
        }
        String cleaned = name.trim();

        Guest guest = findOrCreateGuest(event, cleaned);
        if (guest.getGuestToken() == null) {
            guest.setGuestToken(UUID.randomUUID().toString());
            guestRepository.saveAndFlush(guest);
        }
        return new GuestClaimResponse(guest.getName(), guest.getGuestToken());
    }

    @Override
    public Optional<Guest> findByToken(String eventCode, String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            return Optional.empty();
        }
        Event event = findByCodeOrThrow(eventCode);
        return guestRepository.findByEventAndGuestToken(event, guestToken);
    }

    @Override
    public PhotoResponse uploadPhoto(String eventCode, String uploaderName, String guestToken,
                                     MultipartFile file, UserDetails userDetails) {
        Event event = findByCodeOrThrow(eventCode);

        validateFile(file);

        String name = resolveUploaderName(event, uploaderName, userDetails);
        Guest guest = guestToken == null
                ? findOrCreateGuest(event, name)
                : findByToken(eventCode, guestToken)
                    .orElseThrow(() -> ApiException.unauthorized("Invalid guest token"));

        String fileName = storeFile(file);
        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setGuest(guest);
        photo.setFileName(fileName);
        photo.setOriginalName(safeOriginalName(file));
        photo.setMimeType(file.getContentType());
        photo.setSize(file.getSize());
        photo.setStatus(PhotoStatus.PROCESSING);
        try {
            photo.setSha256(sha256Of(file));
        } catch (Exception ignored) {
        }
        Photo savedPhoto = photoRepository.save(photo);

        mediaQueueService.enqueue(savedPhoto.getId());
        realtimeEventsService.photoAdded(savedPhoto);
        return PhotoResponse.from(savedPhoto);
    }

    @Override
    public List<PhotoResponse> getPhotosByUploader(String eventCode, String uploaderName, String guestToken,
                                                   Long beforeId, int limit) {
        Event event = findByCodeOrThrow(eventCode);
        Optional<Guest> guest;
        if (guestToken != null && !guestToken.isBlank()) {
            guest = guestRepository.findByEventAndGuestToken(event, guestToken);
        } else if (uploaderName != null && !uploaderName.trim().isEmpty()) {
            guest = guestRepository.findByEventAndName(event, uploaderName.trim());
        } else {
            return List.of();
        }
        if (guest.isEmpty()) {
            return List.of();
        }
        List<Photo> photos = beforeId != null
                ? photoRepository.findByGuestAndIdLessThanOrderByUploadedAtDescIdDesc(
                        guest.get(), beforeId, PageRequest.of(0, Math.min(limit, 200))).getContent()
                : photoRepository.findByGuestOrderByUploadedAtDesc(guest.get());
        return photos.stream().map(PhotoResponse::from).collect(Collectors.toList());
    }

    @Override
    @jakarta.transaction.Transactional
    public void deletePhoto(String eventCode, String guestToken, Long photoId) {
        Event event = findByCodeOrThrow(eventCode);
        Guest guest = findByToken(eventCode, guestToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid guest token"));

        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> ApiException.notFound("Photo not found"));

        if (!photo.getEvent().getId().equals(event.getId()) || !photo.getGuest().getId().equals(guest.getId())) {
            throw ApiException.forbidden("You can only remove photos you uploaded");
        }

        deletePhotoFiles(photo);
        realtimeEventsService.photoRemoved(event.getId(), photo.getId());
        realtimeEventsService.activity(event.getId(), guest.getName() + " removed a photo");
        photoRepository.delete(photo);
    }

    private void deleteFileIfExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(uploadDir, fileName));
        } catch (IOException ignored) {
        }
    }

    private void deletePhotoFiles(Photo photo) {
        deleteFileIfExists(photo.getFileName());
        deleteFileIfExists(photo.getFileNameThumb());
        deleteFileIfExists(photo.getFileNameMed());
        deleteFileIfExists(photo.getFileNameFull());
        deleteFileIfExists(photo.getFileNameWeb());
        deleteFileIfExists(photo.getFileNamePoster());
    }

    private Event findByCodeOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> ApiException.notFound("Event not found"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        String contentType = file.getContentType();
        boolean allowed = contentType != null && (
                contentType.startsWith("image/") ||
                "video/mp4".equalsIgnoreCase(contentType) ||
                "video/quicktime".equalsIgnoreCase(contentType) ||
                "video/webm".equalsIgnoreCase(contentType)
        );
        if (!allowed) {
            throw ApiException.badRequest("Unsupported file type. Only images and videos are allowed.");
        }
    }

    private String resolveUploaderName(Event event, String uploaderName, UserDetails userDetails) {
        if (userDetails != null) {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> ApiException.notFound("User not found"));
            return user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                    ? user.getDisplayName()
                    : user.getUsername();
        }
        if (uploaderName == null || uploaderName.trim().isEmpty()) {
            throw ApiException.badRequest("A display name is required to upload photos");
        }
        String name = uploaderName.trim();
        if (name.length() < 2) {
            throw ApiException.badRequest("Display name must be at least 2 characters long");
        }
        return name;
    }

    private Guest findOrCreateGuest(Event event, String name) {
        Guest guest = guestRepository.findByEventAndName(event, name).orElse(null);
        if (guest != null) {
            return guest;
        }
        guest = new Guest();
        guest.setEvent(event);
        guest.setName(name);
        try {
            guestRepository.saveAndFlush(guest);
        } catch (DataIntegrityViolationException e) {
            guest = guestRepository.findByEventAndName(event, name)
                    .orElseThrow(() -> ApiException.conflict("This name is already taken in this event"));
        }
        return guest;
    }

    private String storeFile(MultipartFile file) {
        String fileName = UUID.randomUUID() + safeExtension(file);
        Path filePath = Paths.get(uploadDir, fileName).toAbsolutePath();
        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
        return fileName;
    }

    private String sha256Of(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var in = file.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash file", e);
        }
    }

    private String safeOriginalName(MultipartFile file) {
        return file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
    }

    private String safeExtension(MultipartFile file) {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        int dot = original.lastIndexOf('.');
        return dot >= 0 ? original.substring(dot).toLowerCase() : "";
    }
}