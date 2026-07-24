package com.mmea.albumy.controller;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events/code")
public class GuestController {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final String uploadDir;

    public GuestController(EventRepository eventRepository, PhotoRepository photoRepository,
                          @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.uploadDir = uploadDir;
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @GetMapping("/{eventCode}")
    public ResponseEntity<EventPublicInfo> getEventPublicInfo(@PathVariable String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        EventPublicInfo info = new EventPublicInfo(
                event.getName(),
                event.getDate(),
                event.getStartTime()
        );
        
        return ResponseEntity.ok(info);
    }

    @GetMapping("/{eventCode}/name-available")
    public ResponseEntity<Boolean> isNameAvailable(@PathVariable String eventCode,
                                                     @RequestParam String name) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        boolean available = !photoRepository.existsByEventAndUploaderName(event, name);
        return ResponseEntity.ok(available);
    }

    @PostMapping("/{eventCode}/photos")
    public ResponseEntity<PhotoResponse> uploadPhoto(@PathVariable String eventCode,
                                                       @RequestParam(required = false) String uploaderName,
                                                       @RequestParam("file") MultipartFile file) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        if (uploaderName == null || uploaderName.trim().isEmpty()) {
            uploaderName = "Anonymous_" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        if (photoRepository.existsByEventAndUploaderName(event, uploaderName)) {
            throw new RuntimeException("Name already taken in this event");
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
        
        PhotoResponse response = new PhotoResponse(
                savedPhoto.getId(),
                savedPhoto.getUploaderName(),
                savedPhoto.getFileName(),
                "/files/" + savedPhoto.getFileName(),
                savedPhoto.getUploadedAt()
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{eventCode}/photos")
    public ResponseEntity<List<PhotoResponse>> getPhotosByUploader(@PathVariable String eventCode,
                                                                    @RequestParam String uploaderName) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        List<Photo> photos = photoRepository.findByEventAndUploaderName(event, uploaderName);
        List<PhotoResponse> responses = photos.stream()
                .map(photo -> new PhotoResponse(
                        photo.getId(),
                        photo.getUploaderName(),
                        photo.getFileName(),
                        "/files/" + photo.getFileName(),
                        photo.getUploadedAt()
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
}
