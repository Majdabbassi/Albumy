package com.mmea.albumy.controller;

import com.mmea.albumy.dto.*;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final String uploadDir;

    public EventController(EventRepository eventRepository, PhotoRepository photoRepository,
                          UserRepository userRepository, @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.uploadDir = uploadDir;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Event event = new Event();
        event.setName(request.getName());
        event.setDate(request.getDate());
        event.setStartTime(request.getStartTime());
        event.setOrganizer(organizer);
        
        String eventCode;
        do {
            eventCode = generateRandomCode(6);
        } while (eventRepository.existsByEventCode(eventCode));
        event.setEventCode(eventCode);
        
        String fullAlbumToken;
        do {
            fullAlbumToken = UUID.randomUUID().toString();
        } while (eventRepository.existsByFullAlbumToken(fullAlbumToken));
        event.setFullAlbumToken(fullAlbumToken);
        
        Event savedEvent = eventRepository.save(event);
        
        return ResponseEntity.ok(toEventResponse(savedEvent));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEvents(@AuthenticationPrincipal UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Event> events = eventRepository.findByOrganizer(organizer);
        List<EventResponse> responses = events.stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> getEvent(@PathVariable Long id,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        List<Photo> photos = photoRepository.findByEvent(event);
        List<PhotoResponse> photoResponses = photos.stream()
                .map(this::toPhotoResponse)
                .collect(Collectors.toList());
        
        EventDetailResponse response = new EventDetailResponse(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getEventCode(),
                event.getFullAlbumToken(),
                event.getOrganizer().getId(),
                event.getCreatedAt(),
                photoResponses,
                photos.size()
        );
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        List<Photo> photos = photoRepository.findByEvent(event);
        photoRepository.deleteAll(photos);
        eventRepository.delete(event);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photos/zip")
    public ResponseEntity<org.springframework.core.io.Resource> downloadEventPhotosAsZip(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        List<Photo> photos = photoRepository.findByEvent(event);
        
        Path zipPath = Files.createTempFile("event-" + id + "-", ".zip");
        
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Photo photo : photos) {
                Path filePath = Paths.get(uploadDir, photo.getFileName());
                if (Files.exists(filePath)) {
                    ZipEntry zipEntry = new ZipEntry(photo.getUploaderName() + "_" + photo.getFileName());
                    zipOut.putNextEntry(zipEntry);
                    
                    try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zipOut.write(buffer, 0, len);
                        }
                    }
                    
                    zipOut.closeEntry();
                }
            }
        }
        
        org.springframework.core.io.Resource resource = new org.springframework.core.io.PathResource(zipPath);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + id + "-photos.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long photoId,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        
        if (!photo.getEvent().getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        Path filePath = Paths.get(uploadDir, photo.getFileName());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log but continue with database deletion
        }
        
        photoRepository.delete(photo);
        
        return ResponseEntity.noContent().build();
    }

    private EventResponse toEventResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getEventCode(),
                event.getFullAlbumToken(),
                event.getOrganizer().getId(),
                event.getCreatedAt()
        );
    }

    private PhotoResponse toPhotoResponse(Photo photo) {
        return new PhotoResponse(
                photo.getId(),
                photo.getUploaderName(),
                photo.getFileName(),
                "/files/" + photo.getFileName(),
                photo.getUploadedAt()
        );
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return code.toString();
    }
}
