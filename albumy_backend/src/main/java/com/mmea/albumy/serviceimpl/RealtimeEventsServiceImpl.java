package com.mmea.albumy.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.service.RealtimeEventsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RealtimeEventsServiceImpl implements RealtimeEventsService {

    public static final String CHANNEL = "albumy:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public RealtimeEventsServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void photoAdded(Photo photo) {
        publish("photos", photo.getEvent().getId(), "PHOTO_ADDED",
                objectMapper.valueToTree(PhotoResponse.from(photo)), null);
    }

    @Override
    public void photoReady(Photo photo) {
        publish("photos", photo.getEvent().getId(), "PHOTO_READY",
                objectMapper.valueToTree(PhotoResponse.from(photo)), null);
    }

    @Override
    public void photoRemoved(Long eventId, Long photoId) {
        publish("photos", eventId, "PHOTO_REMOVED", Map.of("id", photoId), null);
    }

    @Override
    public void activity(Long eventId, String message) {
        publish("activity", eventId, "ACTIVITY", null, message);
    }

    private void publish(String topic, Long eventId, String type, Object data, String message) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("topic", topic);
            payload.put("eventId", eventId);
            payload.put("type", type);
            if (data != null) {
                payload.put("data", data);
            }
            if (message != null) {
                payload.put("message", message);
            }
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // Never let a publish failure take down an upload.
        }
    }
}