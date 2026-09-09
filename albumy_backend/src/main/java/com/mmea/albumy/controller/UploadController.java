package com.mmea.albumy.controller;

import com.mmea.albumy.dto.PhotoResponse;
import com.mmea.albumy.dto.UploadInitRequest;
import com.mmea.albumy.dto.UploadInitResponse;
import com.mmea.albumy.service.UploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public ResponseEntity<UploadInitResponse> init(@RequestBody UploadInitRequest request) {
        return ResponseEntity.ok(uploadService.init(request));
    }

    @PutMapping("/{uploadId}/chunks/{chunkIndex}")
    public ResponseEntity<Void> storeChunk(@PathVariable String uploadId,
                                           @PathVariable int chunkIndex,
                                           @RequestBody byte[] data) {
        uploadService.storeChunk(uploadId, chunkIndex, data);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{uploadId}/chunks")
    public ResponseEntity<List<Integer>> receivedChunks(@PathVariable String uploadId) {
        return ResponseEntity.ok(uploadService.receivedChunks(uploadId));
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<PhotoResponse> complete(@PathVariable String uploadId) {
        return ResponseEntity.ok(uploadService.complete(uploadId));
    }
}