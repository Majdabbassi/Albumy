package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponse {
    private String token;
    private String registrationUrl;
    private boolean used;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}