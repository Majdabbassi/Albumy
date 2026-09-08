package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.service.GuestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final String uploadDir;

    public GuestServiceImpl(EventRepository eventRepository, GuestRepository guestRepository,
                           PhotoRepository photoRepository, UserRepository userRepository,
                           @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.guestRepository = guestRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
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
                event.getName(),
                event.getDate(),
                event.getStartTime()
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
    public PhotoResponse uploadPhoto(String eventCode, String uploaderName, MultipartFile file, UserDetails userDetails) {
        Event event = findByCodeOrThrow(eventCode);

        validateFile(file);

        String name = resolveUploaderName(event, uploaderName, userDetails);
        Guest guest = findOrCreateGuest(event, name);

        String fileName = storeFile(file);

        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setGuest(guest);
        photo.setFileName(fileName);
        Photo savedPhoto = photoRepository.save(photo);

        return new PhotoResponse(
                savedPhoto.getId(),
                guest.getName(),
                savedPhoto.getFileName(),
                "/files/" + savedPhoto.getFileName(),
                savedPhoto.getUploadedAt()
        );
    }

    @Override
    public List<PhotoResponse> getPhotosByUploader(String eventCode, String uploaderName) {
        Event event = findByCodeOrThrow(eventCode);

        if (uploaderName == null || uploaderName.trim().isEmpty()) {
            return List.of();
        }

        Optional<Guest> guest = guestRepository.findByEventAndName(event, uploaderName.trim());
        if (guest.isEmpty()) {
            return List.of();
        }

        return photoRepository.findByGuest(guest.get()).stream()
                .map(this::toPhotoResponse)
                .collect(Collectors.toList());
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
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot) : "";
        String fileName = UUID.randomUUID() + extension;

        Path filePath = Paths.get(uploadDir, fileName).toAbsolutePath();
        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
        return fileName;
    }

    private PhotoResponse toPhotoResponse(Photo photo) {
        return new PhotoResponse(
                photo.getId(),
                photo.getGuest().getName(),
                photo.getFileName(),
                "/files/" + photo.getFileName(),
                photo.getUploadedAt()
        );
    }
}