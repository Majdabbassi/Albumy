package com.mmea.albumy.controller;

import com.mmea.albumy.service.FileService;
import com.mmea.albumy.util.MediaFileTypes;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> getFile(@PathVariable String fileName) throws IOException {
        if (!isSafeFileName(fileName)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileService.getFile(fileName);

        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = resource.getFile().toPath();
        String contentType = MediaFileTypes.mimeFor(fileName);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        ContentDisposition disposition = ContentDisposition.builder("inline")
                .filename(filePath.getFileName().toString(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(resource);
    }

    private boolean isSafeFileName(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return false;
        }
        if (!fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            return false;
        }
        return MediaFileTypes.isAllowedExtension(fileName.substring(dot + 1));
    }
}