package com.mmea.albumy.controller;

import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events/full")
public class FullAlbumController {

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;

    public FullAlbumController(EventRepository eventRepository, PhotoRepository photoRepository) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
    }

    @GetMapping("/{fullAlbumToken}")
    public ResponseEntity<EventDetailResponse> getFullAlbum(@PathVariable String fullAlbumToken) {
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
}
