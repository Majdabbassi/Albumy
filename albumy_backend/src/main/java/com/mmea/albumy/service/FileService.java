package com.mmea.albumy.service;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface FileService {
    Resource getFile(String fileName) throws IOException;
}
