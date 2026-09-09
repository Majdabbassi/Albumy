package com.mmea.albumy.service;

import com.mmea.albumy.dto.CreateEventRequest;
import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.dto.EventResponse;
import org.springframework.core.io.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface EventService {
    EventResponse createEvent(CreateEventRequest request, UserDetails userDetails);
    List<EventResponse> getEvents(UserDetails userDetails);
    EventDetailResponse getEvent(Long id, UserDetails userDetails, Long beforeId, int limit);
    void deleteEvent(Long id, UserDetails userDetails);
    EventResponse updateCover(Long id, MultipartFile file, UserDetails userDetails);
    EventResponse updateEvent(Long id, CreateEventRequest request, UserDetails userDetails);
    Resource downloadEventPhotosAsZip(Long id, UserDetails userDetails) throws IOException;
    void deletePhoto(Long photoId, UserDetails userDetails);
}
