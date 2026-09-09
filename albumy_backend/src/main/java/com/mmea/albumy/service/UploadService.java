package com.mmea.albumy.service;

import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.dto.UploadInitRequest;
import com.mmea.albumy.dto.UploadInitResponse;

import java.util.List;

public interface UploadService {
    UploadInitResponse init(UploadInitRequest request);
    void storeChunk(String uploadId, int chunkIndex, byte[] data);
    List<Integer> receivedChunks(String uploadId);
    PhotoResponse complete(String uploadId);
}