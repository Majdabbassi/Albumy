package com.mmea.albumy.service;

import com.mmea.albumy.dto.EventPublicInfo;
import com.mmea.albumy.dto.PhotoResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GuestService {
    EventPublicInfo getEventPublicInfo(String eventCode);
    Boolean isNameAvailable(String eventCode, String name);
    PhotoResponse uploadPhoto(String eventCode, String uploaderName, MultipartFile file, UserDetails userDetails);
    List<PhotoResponse> getPhotosByUploader(String eventCode, String uploaderName);
}
