package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.service.FullAlbumService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FullAlbumServiceImpl implements FullAlbumService {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;

    public FullAlbumServiceImpl(EventRepository eventRepository, PhotoRepository photoRepository) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetailResponse getFullAlbum(String fullAlbumToken, Long beforeId, int limit) {
        Event event = eventRepository.findByFullAlbumToken(fullAlbumToken)
                .orElseThrow(() -> ApiException.notFound("Event not found"));

        List<Photo> photos = beforeId != null
                ? photoRepository.findByEventAndIdLessThanOrderByUploadedAtDescIdDesc(
                        event, beforeId, PageRequest.of(0, Math.min(limit, 200))).getContent()
                : photoRepository.findByEventOrderByUploadedAtDesc(event);
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
                photos.size(),
                event.getCoverFileName() == null ? null : "/files/" + event.getCoverFileName()
        );
    }
}