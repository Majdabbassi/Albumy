package com.mmea.albumy.controller;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.GuestClaimRequest;
import com.mmea.albumy.dto.GuestClaimResponse;
import com.mmea.albumy.dto.GuestPhotosResponse;
import com.mmea.albumy.service.GuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events/code")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @GetMapping("/{eventCode}")
    public ResponseEntity<EventPublicInfo> getEventPublicInfo(@PathVariable String eventCode) {
        EventPublicInfo info = guestService.getEventPublicInfo(eventCode);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/{eventCode}/name-available")
    public ResponseEntity<Boolean> isNameAvailable(@PathVariable String eventCode,
                                                     @RequestParam String name) {
        Boolean available = guestService.isNameAvailable(eventCode, name);
        return ResponseEntity.ok(available);
    }

    @PostMapping("/{eventCode}/claim")
    public ResponseEntity<GuestClaimResponse> claimGuest(@PathVariable String eventCode,
                                                         @RequestBody GuestClaimRequest request) {
        GuestClaimResponse response = guestService.claimGuest(eventCode, request.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{eventCode}/photos")
    public ResponseEntity<GuestPhotosResponse> getPhotosByUploader(@PathVariable String eventCode,
                                                                   @RequestParam(required = false) String uploaderName,
                                                                   @RequestParam(required = false) Long beforeId,
                                                                   @RequestParam(defaultValue = "60") int limit,
                                                                   @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        GuestPhotosResponse responses = guestService.getPhotosByUploader(eventCode, uploaderName, guestToken, beforeId, limit);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{eventCode}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable String eventCode,
                                            @PathVariable Long photoId,
                                            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        guestService.deletePhoto(eventCode, guestToken, photoId);
        return ResponseEntity.noContent().build();
    }
}