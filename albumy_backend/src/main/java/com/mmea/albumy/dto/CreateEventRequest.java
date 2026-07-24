package com.mmea.albumy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private LocalDate date;
    
    @NotBlank
    private LocalTime startTime;
}
