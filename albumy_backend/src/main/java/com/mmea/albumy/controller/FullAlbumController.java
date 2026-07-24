package com.mmea.albumy.controller;

import com.mmea.albumy.dto.EventDetailResponse;
import com.mmea.albumy.service.FullAlbumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events/full")
public class FullAlbumController {

    private final FullAlbumService fullAlbumService;

    public FullAlbumController(FullAlbumService fullAlbumService) {
        this.fullAlbumService = fullAlbumService;
    }

    @GetMapping("/{fullAlbumToken}")
    public ResponseEntity<EventDetailResponse> getFullAlbum(@PathVariable String fullAlbumToken) {
        EventDetailResponse response = fullAlbumService.getFullAlbum(fullAlbumToken);
        return ResponseEntity.ok(response);
    }
}
