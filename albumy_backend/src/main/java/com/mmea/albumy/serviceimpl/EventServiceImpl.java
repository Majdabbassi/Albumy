package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.CreateEventRequest;
import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.EventResponse;
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
import com.mmea.albumy.service.EventService;
import com.mmea.albumy.service.RealtimeEventsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EventServiceImpl implements EventService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final GuestRepository guestRepository;
private final UserRepository userRepository;
    private final RealtimeEventsService realtimeEventsService;
    private final String uploadDir;

    public EventServiceImpl(EventRepository eventRepository, PhotoRepository photoRepository,
                            GuestRepository guestRepository, UserRepository userRepository,
                            RealtimeEventsService realtimeEventsService,
                            @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.guestRepository = guestRepository;
        this.userRepository = userRepository;
        this.realtimeEventsService = realtimeEventsService;
        this.uploadDir = uploadDir;
    }

    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (organizer.getRole() != User.Role.ORGANIZER) {
            throw ApiException.forbidden("Only organizers can create events");
        }

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
    @Transactional(readOnly = true)
    public List<EventResponse> getEvents(UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

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
    @Transactional
    public EventResponse updateEvent(Long id, CreateEventRequest request, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this event");
        }

        event.setName(request.getName());
        event.setDate(request.getDate());
        event.setStartTime(request.getStartTime());
        return toEventResponse(eventRepository.save(event));
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(Long id, UserDetails userDetails, Long beforeId, int limit) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this event");
        }

        List<Photo> photos = beforeId != null
                ? photoRepository.findByEventAndIdLessThanOrderByUploadedAtDescIdDesc(
                        event, beforeId, PageRequest.of(0, Math.min(limit, 200))).getContent()
                : photoRepository.findByEventOrderByUploadedAtDesc(event);
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
                photos.size(),
                coverUrl(event)
        );
    }

    @Override
    @Transactional
    public void deleteEvent(Long id, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this event");
        }

        List<Photo> photos = photoRepository.findByEvent(event);
        for (Photo photo : photos) {
            deletePhotoFiles(photo);
            realtimeEventsService.photoRemoved(event.getId(), photo.getId());
        }
        photoRepository.deleteAll(photos);

        deleteFileIfExists(event.getCoverFileName());

        List<Guest> guests = guestRepository.findByEvent(event);
        guestRepository.deleteAll(guests);

        eventRepository.delete(event);
    }

    @Override
    @Transactional
    public EventResponse updateCover(Long id, MultipartFile file, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this event");
        }

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("Cover must be an image");
        }

        String previous = event.getCoverFileName();
        String fileName = storeFile(file);
        event.setCoverFileName(fileName);
        eventRepository.save(event);
        deleteFileIfExists(previous);

        return toEventResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadEventPhotosAsZip(Long id, UserDetails userDetails) throws IOException {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this event");
        }

        List<Photo> photos = photoRepository.findByEvent(event);

        Path zipPath = Files.createTempFile("event-" + id + "-", ".zip");

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Photo photo : photos) {
                Path filePath = Paths.get(uploadDir, photo.getFileName());
                if (Files.exists(filePath)) {
                    ZipEntry zipEntry = new ZipEntry(photo.getGuest().getName() + "_" + photo.getFileName());
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
    @Transactional
    public void deletePhoto(Long photoId, UserDetails userDetails) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> ApiException.notFound("Photo not found"));

        if (organizer.getRole() != User.Role.ADMIN && !photo.getEvent().getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this photo");
        }

        deletePhotoFiles(photo);
        realtimeEventsService.photoRemoved(photo.getEvent().getId(), photo.getId());
        photoRepository.delete(photo);
    }

    private EventResponse toEventResponse(Event event) {
        String coverThumb = photoRepository
                .findFirstByEventAndStatusOrderByUploadedAtDesc(event, PhotoStatus.READY)
                .map(Photo::getFileNameThumb)
                .orElse(null);
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getEventCode(),
                event.getFullAlbumToken(),
                event.getOrganizer().getId(),
                event.getCreatedAt(),
                photoRepository.countByEvent(event),
                coverThumb,
                coverUrl(event)
        );
    }

    private String coverUrl(Event event) {
        return event.getCoverFileName() == null
                ? null
                : "/files/" + event.getCoverFileName();
    }

    private String storeFile(MultipartFile file) {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        int dot = original.lastIndexOf('.');
        String extension = dot >= 0 ? original.substring(dot).toLowerCase() : "";
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
        return PhotoResponse.from(photo);
    }

    private void deleteFileIfExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        Path filePath = Paths.get(uploadDir, fileName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log but continue with database deletion
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

    private String generateRandomCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }
}