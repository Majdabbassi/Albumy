package com.mmea.albumy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestPhotosResponse {
    private List<PhotoResponse> photos;
    private long photoCount;
    private boolean hasMore;
}