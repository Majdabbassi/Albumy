package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadInitResponse {
    private String uploadId;
    private int chunkSize;
}