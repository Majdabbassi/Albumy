package com.mmea.albumy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {
    @NotBlank(message = "Event name is required")
    @Size(max = 100, message = "Event name must be 100 characters or fewer")
    private String name;
    
    @NotNull
    private LocalDate date;
    
    @NotNull
    private LocalTime startTime;
}
