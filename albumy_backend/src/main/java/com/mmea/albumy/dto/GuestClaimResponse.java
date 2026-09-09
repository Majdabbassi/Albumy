package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuestClaimResponse {
    private String guestName;
    private String guestToken;
}