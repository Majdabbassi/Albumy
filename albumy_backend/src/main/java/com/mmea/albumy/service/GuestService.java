package com.mmea.albumy.service;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.GuestClaimResponse;
import com.mmea.albumy.dto.GuestPhotosResponse;
import com.mmea.albumy.model.Guest;

import java.util.Optional;

public interface GuestService {
    EventPublicInfo getEventPublicInfo(String eventCode);
    Boolean isNameAvailable(String eventCode, String name);
    GuestClaimResponse claimGuest(String eventCode, String name);
    Optional<Guest> findByToken(String eventCode, String guestToken);
    GuestPhotosResponse getPhotosByUploader(String eventCode, String uploaderName, String guestToken,
                                            Long beforeId, int limit);
    void deletePhoto(String eventCode, String guestToken, Long photoId);
}