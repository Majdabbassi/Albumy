package com.mmea.albumy.controller;

import com.mmea.albumy.dto.CreateEventRequest;
import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.EventResponse;
import com.mmea.albumy.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        EventResponse response = eventService.createEvent(request, userDetails);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEvents(@AuthenticationPrincipal UserDetails userDetails) {
        List<EventResponse> responses = eventService.getEvents(userDetails);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> getEvent(@PathVariable Long id,
                                                        @RequestParam(required = false) Long beforeId,
                                                        @RequestParam(defaultValue = "60") int limit,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        EventDetailResponse response = eventService.getEvent(id, userDetails, beforeId, limit);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id,
                                                     @Valid @RequestBody CreateEventRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        EventResponse response = eventService.updateEvent(id, request, userDetails);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        eventService.deleteEvent(id, userDetails);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photos/zip")
    public ResponseEntity<org.springframework.core.io.Resource> downloadEventPhotosAsZip(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        org.springframework.core.io.Resource resource = eventService.downloadEventPhotosAsZip(id, userDetails);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + id + "-photos.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long photoId,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        eventService.deletePhoto(photoId, userDetails);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cover")
    public ResponseEntity<EventResponse> updateCover(@PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        EventResponse response = eventService.updateCover(id, file, userDetails);
        return ResponseEntity.ok(response);
    }
}
