package com.mmea.albumy.dto;

import com.mmea.albumy.model.Photo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhotoResponse {
    private Long id;
    private String uploaderName;
    private String originalName;
    private String fileName;
    private String fileUrl;
    private String thumbUrl;
    private String medUrl;
    private String fullUrl;
    private String posterUrl;
    private String webUrl;
    private boolean video;
    private Long duration;
    private Integer width;
    private Integer height;
    private Long size;
    private String status;
    private LocalDate captureDate;
    private LocalDateTime uploadedAt;

    public static PhotoResponse from(Photo photo) {
        boolean video = photo.getMimeType() != null && photo.getMimeType().startsWith("video/");
        return new PhotoResponse(
                photo.getId(),
                photo.getGuest().getName(),
                photo.getOriginalName(),
                photo.getFileName(),
                "/files/" + photo.getFileName(),
                photo.getFileNameThumb() != null ? "/files/" + photo.getFileNameThumb() : null,
                photo.getFileNameMed() != null ? "/files/" + photo.getFileNameMed() : null,
                photo.getFileNameFull() != null ? "/files/" + photo.getFileNameFull() : null,
                photo.getFileNamePoster() != null ? "/files/" + photo.getFileNamePoster() : null,
                photo.getFileNameWeb() != null ? "/files/" + photo.getFileNameWeb() : null,
                video,
                photo.getDuration(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getSize(),
                photo.getStatus().name(),
                photo.getCaptureDate(),
                photo.getUploadedAt()
        );
    }
}