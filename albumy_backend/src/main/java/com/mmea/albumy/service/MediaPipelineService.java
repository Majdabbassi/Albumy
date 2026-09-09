package com.mmea.albumy.service;

import com.mmea.albumy.model.Photo;

public interface MediaPipelineService {
    void process(Photo photo) throws Exception;
}