package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhotoResponse {
    private Long id;
    private String uploaderName;
    private String fileName;
    private String fileUrl;
    private LocalDateTime uploadedAt;
}
