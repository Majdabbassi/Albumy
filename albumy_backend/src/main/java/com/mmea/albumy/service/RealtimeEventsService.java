package com.mmea.albumy.service;

import com.mmea.albumy.model.Photo;

public interface RealtimeEventsService {
    void photoAdded(Photo photo);
    void photoReady(Photo photo);
    void photoRemoved(Long eventId, Long photoId);
    void activity(Long eventId, String message);
}