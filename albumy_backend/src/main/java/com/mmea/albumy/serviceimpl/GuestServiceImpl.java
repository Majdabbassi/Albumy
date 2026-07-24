package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.service.GuestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GuestServiceImpl implements GuestService {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final String uploadDir;

    public GuestServiceImpl(EventRepository eventRepository, PhotoRepository photoRepository,
                           UserRepository userRepository, @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
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
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return new EventPublicInfo(
                event.getName(),
                event.getDate(),
                event.getStartTime()
        );
    }

    @Override
    public Boolean isNameAvailable(String eventCode, String name) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return !photoRepository.existsByEventAndUploaderName(event, name);
    }

    @Override
    public PhotoResponse uploadPhoto(String eventCode, String uploaderName, MultipartFile file, UserDetails userDetails) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // If user is authenticated, use their account identity
        if (userDetails != null) {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            uploaderName = user.getDisplayName() != null && !user.getDisplayName().isEmpty() ? user.getDisplayName() : user.getUsername();
            // Skip uniqueness check for authenticated users - their account is already unique
        } else {
            // Anonymous flow - require name uniqueness check
            if (uploaderName == null || uploaderName.trim().isEmpty()) {
                uploaderName = "Anonymous_" + UUID.randomUUID().toString().substring(0, 8);
            }

            if (photoRepository.existsByEventAndUploaderName(event, uploaderName)) {
                throw new RuntimeException("Name already taken in this event");
            }
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = UUID.randomUUID().toString() + fileExtension;

        Path filePath = Paths.get(uploadDir, fileName);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }

        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setUploaderName(uploaderName);
        photo.setFileName(fileName);
        Photo savedPhoto = photoRepository.save(photo);

        return new PhotoResponse(
                savedPhoto.getId(),
                savedPhoto.getUploaderName(),
                savedPhoto.getFileName(),
                "/files/" + savedPhoto.getFileName(),
                savedPhoto.getUploadedAt()
        );
    }

    @Override
    public List<PhotoResponse> getPhotosByUploader(String eventCode, String uploaderName) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<Photo> photos = photoRepository.findByEventAndUploaderName(event, uploaderName);
        return photos.stream()
                .map(photo -> new PhotoResponse(
                        photo.getId(),
                        photo.getUploaderName(),
                        photo.getFileName(),
                        "/files/" + photo.getFileName(),
                        photo.getUploadedAt()
                ))
                .collect(Collectors.toList());
    }
}
