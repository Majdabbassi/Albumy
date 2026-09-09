package com.mmea.albumy.service;

import com.mmea.albumy.dto.EventDetailResponse;

public interface FullAlbumService {
    EventDetailResponse getFullAlbum(String fullAlbumToken, Long beforeId, int limit);
}
