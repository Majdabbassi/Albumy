package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.GuestClaimResponse;
import com.mmea.albumy.dto.GuestPhotosResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.security.RealtimeTicketService;
import com.mmea.albumy.service.GuestService;
import com.mmea.albumy.service.RealtimeEventsService;
import com.mmea.albumy.util.FileUrls;
import com.mmea.albumy.util.PhotoFiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GuestServiceImpl implements GuestService {

    private static final Pattern EVENT_CODE = Pattern.compile("^[A-Z0-9]{6}$");
    private static final int MAX_GUEST_NAME = 100;

    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final PhotoRepository photoRepository;
    private final RealtimeEventsService realtimeEventsService;
    private final RealtimeTicketService realtimeTicketService;
    private final String uploadDir;

    public GuestServiceImpl(EventRepository eventRepository, GuestRepository guestRepository,
                            PhotoRepository photoRepository,
                            RealtimeEventsService realtimeEventsService,
                            RealtimeTicketService realtimeTicketService,
                            @Value("${upload.dir:uploads}") String uploadDir) {
        this.eventRepository = eventRepository;
        this.guestRepository = guestRepository;
        this.photoRepository = photoRepository;
        this.realtimeEventsService = realtimeEventsService;
        this.realtimeTicketService = realtimeTicketService;
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
                FileUrls.coverUrl(event),
                realtimeTicketService.issue(event.getId())
        );
    }

    @Override
    public Boolean isNameAvailable(String eventCode, String name) {
        Event event = findByCodeOrThrow(eventCode);
        if (name == null || name.trim().length() < 2 || name.trim().length() > MAX_GUEST_NAME) {
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
        if (cleaned.length() > MAX_GUEST_NAME) {
            throw ApiException.badRequest("Display name must be " + MAX_GUEST_NAME + " characters or fewer");
        }
        if (cleaned.chars().anyMatch(c -> c < 0x20 || c == '<' || c == '>')) {
            throw ApiException.badRequest("Display name contains unsupported characters");
        }

        Guest guest = findOrCreateGuest(event, cleaned);
        if (guest.getGuestToken() == null) {
            String token = UUID.randomUUID().toString();
            int assigned = guestRepository.assignTokenIfNull(guest.getId(), token);
            if (assigned == 0) {
                Guest other = guestRepository.findById(guest.getId())
                        .orElseThrow(() -> ApiException.conflict("Could not claim this guest name"));
                return new GuestClaimResponse(other.getName(), other.getGuestToken());
            }
            guest.setGuestToken(token);
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
    public GuestPhotosResponse getPhotosByUploader(String eventCode, String uploaderName, String guestToken,
                                                   Long beforeId, int limit) {
        Event event = findByCodeOrThrow(eventCode);
        Optional<Guest> guest;
        if (guestToken != null && !guestToken.isBlank()) {
            guest = guestRepository.findByEventAndGuestToken(event, guestToken);
        } else if (uploaderName != null && !uploaderName.trim().isEmpty()) {
            guest = guestRepository.findByEventAndName(event, uploaderName.trim());
        } else {
            return new GuestPhotosResponse(List.of(), 0L, false);
        }
        if (guest.isEmpty()) {
            return new GuestPhotosResponse(List.of(), 0L, false);
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Page<Photo> page = beforeId != null
                ? photoRepository.findByGuestAndIdLessThanOrderByUploadedAtDescIdDesc(
                        guest.get(), beforeId, PageRequest.of(0, safeLimit))
                : photoRepository.findByGuestOrderByUploadedAtDescIdDesc(guest.get(), PageRequest.of(0, safeLimit));
        List<PhotoResponse> responses = page.getContent().stream()
                .map(PhotoResponse::from)
                .collect(Collectors.toList());
        return new GuestPhotosResponse(responses, page.getTotalElements(), page.hasNext());
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

        PhotoFiles.deleteAfterCommit(() -> PhotoFiles.deletePhotoFiles(uploadDir, photo));
        realtimeEventsService.photoRemoved(event.getId(), photo.getId());
        realtimeEventsService.activity(event.getId(), guest.getName() + " removed a photo");
        photoRepository.delete(photo);
    }

    private Event findByCodeOrThrow(String eventCode) {
        if (eventCode == null || !EVENT_CODE.matcher(eventCode).matches()) {
            throw ApiException.badRequest("Invalid event code");
        }
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> ApiException.notFound("Event not found"));
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
}