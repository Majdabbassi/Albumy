package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailResponse {
    private Long id;
    private String name;
    private LocalDate date;
    private LocalTime startTime;
    private String eventCode;
    private String fullAlbumToken;
    private Long organizerId;
    private LocalDateTime createdAt;
    private List<PhotoResponse> photos;
    private long photoCount;
}
