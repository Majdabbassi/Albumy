package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.CreateEventRequest;
import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.EventResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.service.EventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

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

@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final String uploadDir;

    public EventServiceImpl(EventRepository eventRepository, PhotoRepository photoRepository,
                           UserRepository userRepository, @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.uploadDir = uploadDir;
    }

    @Override
    public EventResponse createEvent(CreateEventRequest request, UserDetails userDetails) {
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

        return toEventResponse(savedEvent);
    }

    @Override
    public List<EventResponse> getEvents(UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Event> events;
        if (organizer.getRole() == User.Role.ADMIN) {
            events = eventRepository.findAll();
        } else {
            events = eventRepository.findByOrganizer(organizer);
        }

        return events.stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());
    }

    @Override
    public EventDetailResponse getEvent(Long id, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Access denied");
        }

        List<Photo> photos = photoRepository.findByEvent(event);
        List<PhotoResponse> photoResponses = photos.stream()
                .map(this::toPhotoResponse)
                .collect(Collectors.toList());

        return new EventDetailResponse(
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
    }

    @Override
    public void deleteEvent(Long id, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("Access denied");
        }

        List<Photo> photos = photoRepository.findByEvent(event);
        photoRepository.deleteAll(photos);
        eventRepository.delete(event);
    }

    @Override
    public Resource downloadEventPhotosAsZip(Long id, UserDetails userDetails) throws IOException {
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

        return new PathResource(zipPath);
    }

    @Override
    public void deletePhoto(Long photoId, UserDetails userDetails) {
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
