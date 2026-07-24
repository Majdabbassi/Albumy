package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponse {
    private String token;
    private String registrationUrl;
}
