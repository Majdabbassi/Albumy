package com.mmea.albumy.dto;

import lombok.Data;

@Data
public class UploadInitRequest {
    private String eventCode;
    private String guestToken;
    private String fileName;
    private String mimeType;
    private int totalChunks;
    private long size;
}