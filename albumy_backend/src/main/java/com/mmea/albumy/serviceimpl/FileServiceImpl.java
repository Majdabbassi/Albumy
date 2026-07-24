package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileServiceImpl implements FileService {

    private final String uploadDir;

    public FileServiceImpl(@Value("${upload.dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public Resource getFile(String fileName) throws IOException {
        Path filePath = Paths.get(uploadDir, fileName);
        Resource resource = new PathResource(filePath);

        if (!resource.exists()) {
            return null;
        }

        return resource;
    }
}
