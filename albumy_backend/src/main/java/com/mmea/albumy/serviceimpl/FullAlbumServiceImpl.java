package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.security.RealtimeTicketService;
import com.mmea.albumy.service.FullAlbumService;
import com.mmea.albumy.util.FileUrls;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FullAlbumServiceImpl implements FullAlbumService {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final RealtimeTicketService realtimeTicketService;

    public FullAlbumServiceImpl(EventRepository eventRepository, PhotoRepository photoRepository,
                                RealtimeTicketService realtimeTicketService) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.realtimeTicketService = realtimeTicketService;
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getFullAlbum(String fullAlbumToken, Long beforeId, int limit) {
        Event event = eventRepository.findByFullAlbumToken(fullAlbumToken)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

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
                photoRepository.countByEvent(event),
                FileUrls.coverUrl(event),
                realtimeTicketService.issue(event.getId())
        );
    }
}