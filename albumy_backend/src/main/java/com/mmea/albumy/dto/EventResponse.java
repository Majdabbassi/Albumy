package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String name;
    private LocalDate date;
    private LocalTime startTime;
    private String eventCode;
    private String fullAlbumToken;
    private Long organizerId;
    private LocalDateTime createdAt;
}
