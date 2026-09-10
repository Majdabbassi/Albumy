package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileServiceImpl implements FileService {

    private final Path uploadRoot;

    public FileServiceImpl(@Value("${upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public Resource getFile(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path filePath = uploadRoot.resolve(fileName).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            return null;
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return null;
        }
        return new PathResource(filePath);
    }
}