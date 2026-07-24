package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.service.FullAlbumService;
import org.springframework.stereotype.Service;

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
    public EventDetailResponse getFullAlbum(String fullAlbumToken) {
        Event event = eventRepository.findByFullAlbumToken(fullAlbumToken)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<Photo> photos = photoRepository.findByEvent(event);
        List<PhotoResponse> photoResponses = photos.stream()
                .map(photo -> new PhotoResponse(
                        photo.getId(),
                        photo.getUploaderName(),
                        photo.getFileName(),
                        "/files/" + photo.getFileName(),
                        photo.getUploadedAt()
                ))
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
}
