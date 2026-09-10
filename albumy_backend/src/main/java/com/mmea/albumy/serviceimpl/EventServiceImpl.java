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
import com.mmea.albumy.security.RealtimeTicketService;
import com.mmea.albumy.service.EventService;
import com.mmea.albumy.service.RealtimeEventsService;
import com.mmea.albumy.util.FileUrls;
import com.mmea.albumy.util.MediaFileTypes;
import com.mmea.albumy.util.PhotoFiles;
import com.mmea.albumy.util.RandomUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EventServiceImpl implements EventService {

    private static final int EVENTS_PAGE_SIZE = 200;

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final GuestRepository guestRepository;
    private final UserRepository userRepository;
    private final RealtimeEventsService realtimeEventsService;
    private final RealtimeTicketService realtimeTicketService;
    private final String uploadDir;

    public EventServiceImpl(EventRepository eventRepository, PhotoRepository photoRepository,
                            GuestRepository guestRepository, UserRepository userRepository,
                            RealtimeEventsService realtimeEventsService,
                            RealtimeTicketService realtimeTicketService,
                            @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.guestRepository = guestRepository;
        this.userRepository = userRepository;
        this.realtimeEventsService = realtimeEventsService;
        this.realtimeTicketService = realtimeTicketService;
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
            eventCode = RandomUtil.generateRandomCode(6);
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
        PageRequest pageRequest = PageRequest.of(0, EVENTS_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (organizer.getRole() == User.Role.ADMIN) {
            events = eventRepository.findAll(pageRequest).getContent();
        } else {
            events = eventRepository.findByOrganizer(organizer, pageRequest).getContent();
        }

        Map<Long, String> coverThumbs = events.isEmpty() ? Map.of() : latestReadyThumbs(events);
        Map<Long, Long> photoCounts = events.isEmpty() ? Map.of() : photoCounts(events);

        return events.stream()
                .map(event -> toEventResponse(
                        event,
                        coverThumbs.get(event.getId()),
                        photoCounts.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private Map<Long, String> latestReadyThumbs(List<Event> events) {
        Map<Long, String> thumbs = new HashMap<>();
        for (Object[] row : photoRepository.findLatestReadyThumbByEvents(events, PhotoStatus.READY)) {
            if (row[0] instanceof Number eventId && row[1] instanceof String thumb) {
                thumbs.putIfAbsent(eventId.longValue(), thumb);
            }
        }
        return thumbs;
    }

    private Map<Long, Long> photoCounts(List<Event> events) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : photoRepository.countByEvents(events)) {
            if (row[0] instanceof Number eventId && row[1] instanceof Number count) {
                counts.put(eventId.longValue(), count.longValue());
            }
        }
        return counts;
    }

    @Override
    @Transactional
    public EventResponse updateEvent(Long id, CreateEventRequest request, UserDetails userDetails) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        requireOrganizerAccess(userDetails, event);

        event.setName(request.getName());
        event.setDate(request.getDate());
        event.setStartTime(request.getStartTime());
        return toEventResponse(eventRepository.save(event));
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(Long id, UserDetails userDetails, Long beforeId, int limit) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        requireOrganizerAccess(userDetails, event);

        int safeLimit = Math.max(1, Math.min(limit, 200));
        Page<Photo> page = beforeId != null
                ? photoRepository.findByEventAndIdLessThanOrderByUploadedAtDescIdDesc(
                        event, beforeId, PageRequest.of(0, safeLimit))
                : photoRepository.findByEventOrderByUploadedAtDescIdDesc(event, PageRequest.of(0, safeLimit));
        List<Photo> photos = page.getContent();
        List<PhotoResponse> photoResponses = photos.stream()
                .map(PhotoResponse::from)
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
                page.getTotalElements(),
                FileUrls.coverUrl(event),
                realtimeTicketService.issue(event.getId())
        );
    }

    @Override
    @Transactional
    public void deleteEvent(Long id, UserDetails userDetails) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        requireOrganizerAccess(userDetails, event);

        long lastId = 0;
        List<Photo> batch;
        do {
            batch = photoRepository.findNextBatch(event, lastId, PageRequest.of(0, 500));
            for (Photo photo : batch) {
                PhotoFiles.deleteAfterCommit(() -> PhotoFiles.deletePhotoFiles(uploadDir, photo));
                realtimeEventsService.photoRemoved(event.getId(), photo.getId());
            }
            photoRepository.deleteAllInBatch(batch);
            if (!batch.isEmpty()) {
                lastId = batch.get(batch.size() - 1).getId();
            }
        } while (batch.size() == 500);

        PhotoFiles.deleteAfterCommit(() -> PhotoFiles.deleteFileIfExists(uploadDir, event.getCoverFileName()));

        List<Guest> guests = guestRepository.findByEvent(event);
        guestRepository.deleteAll(guests);

        eventRepository.delete(event);
    }

    @Override
    @Transactional
    public EventResponse updateCover(Long id, MultipartFile file, UserDetails userDetails) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        requireOrganizerAccess(userDetails, event);

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/") || MediaFileTypes.isBlockedMime(contentType)) {
            throw ApiException.badRequest("Cover must be an image");
        }
        if (MediaFileTypes.storedExtension(file.getOriginalFilename()).isEmpty()) {
            throw ApiException.badRequest("Cover must be an image");
        }

        String previous = event.getCoverFileName();
        String fileName = storeFile(file);
        event.setCoverFileName(fileName);
        eventRepository.save(event);
        PhotoFiles.deleteAfterCommit(() -> PhotoFiles.deleteFileIfExists(uploadDir, previous));

        return toEventResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public void writeEventPhotosAsZip(Long id, UserDetails userDetails, OutputStream out) throws IOException {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        requireOrganizerAccess(userDetails, event);

        List<Photo> photos = photoRepository.findWithGuestsByEvent(event);

        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (Photo photo : photos) {
                Path filePath = Paths.get(uploadDir, photo.getFileName()).toAbsolutePath().normalize();
                if (!Files.exists(filePath)) {
                    continue;
                }
                ZipEntry zipEntry = new ZipEntry(safeZipEntryName(photo));
                zipOut.putNextEntry(zipEntry);

                try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, len);
                    }
                }

                zipOut.closeEntry();
            }
        }
    }

    private String safeZipEntryName(Photo photo) {
        String guestName = photo.getGuest().getName();
        String base = photo.getOriginalName() != null && !photo.getOriginalName().isBlank()
                ? photo.getOriginalName()
                : photo.getFileName();
        String clean = sanitizeZipSegment(guestName);
        String name = sanitizeZipSegment(base);
        if (name.length() > 180) {
            name = name.substring(0, 180);
        }
        return clean + "_" + name;
    }

    private String sanitizeZipSegment(String value) {
        String clean = value == null ? "" : value;
        clean = clean.replaceAll("[\\\\/:*?\"<>|]", "_");
        clean = clean.replaceAll("\\.{2,}", "_");
        return clean.isBlank() ? "guest" : clean;
    }

    @Override
    @Transactional
    public void deletePhoto(Long photoId, UserDetails userDetails) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> ApiException.notFound("Photo not found"));

        requireOrganizerAccess(userDetails, photo);

        PhotoFiles.deleteAfterCommit(() -> PhotoFiles.deletePhotoFiles(uploadDir, photo));
        realtimeEventsService.photoRemoved(photo.getEvent().getId(), photo.getId());
        photoRepository.delete(photo);
    }

    private EventResponse toEventResponse(Event event) {
        String coverThumb = photoRepository
                .findFirstByEventAndStatusOrderByUploadedAtDesc(event, PhotoStatus.READY)
                .map(Photo::getFileNameThumb)
                .orElse(null);
        return toEventResponse(event, coverThumb, photoRepository.countByEvent(event));
    }

    private EventResponse toEventResponse(Event event, String coverThumb, Long photoCount) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getEventCode(),
                event.getFullAlbumToken(),
                event.getOrganizer().getId(),
                event.getCreatedAt(),
                photoCount,
                coverThumb,
                FileUrls.coverUrl(event)
        );
    }

    private String storeFile(MultipartFile file) {
        String extension = MediaFileTypes.storedExtension(file.getOriginalFilename());
        if (extension.isEmpty()) {
            throw ApiException.badRequest("Unsupported file type. Only images are allowed.");
        }
        String fileName = UUID.randomUUID() + "." + extension;
        Path filePath = Paths.get(uploadDir, fileName).toAbsolutePath();
        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
        return fileName;
    }

    private User requireOrganizerAccess(UserDetails userDetails, Event event) {
        User organizer = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (organizer.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(organizer.getId())) {
            throw ApiException.forbidden("You do not have access to this event");
        }
        return organizer;
    }

    private User requireOrganizerAccess(UserDetails userDetails, Photo photo) {
        return requireOrganizerAccess(userDetails, photo.getEvent());
    }
}