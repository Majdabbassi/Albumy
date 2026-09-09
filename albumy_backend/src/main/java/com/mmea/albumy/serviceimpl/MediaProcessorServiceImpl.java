package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.service.MediaPipelineService;
import com.mmea.albumy.service.MediaQueueService;
import com.mmea.albumy.service.RealtimeEventsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaProcessorServiceImpl {

    public static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(MediaProcessorServiceImpl.class);

    private final PhotoRepository photoRepository;
    private final MediaPipelineService mediaPipeline;
    private final MediaQueueService mediaQueueService;
    private final RealtimeEventsService realtimeEventsService;

    public MediaProcessorServiceImpl(PhotoRepository photoRepository,
                                     MediaPipelineService mediaPipeline,
                                     MediaQueueService mediaQueueService,
                                     RealtimeEventsService realtimeEventsService) {
        this.photoRepository = photoRepository;
        this.mediaPipeline = mediaPipeline;
        this.mediaQueueService = mediaQueueService;
        this.realtimeEventsService = realtimeEventsService;
    }

    @Transactional
    public void process(Long photoId) {
        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null || photo.getStatus() == PhotoStatus.READY) {
            return;
        }
        try {
            mediaPipeline.process(photo);
            photo.setStatus(PhotoStatus.READY);
            photo.setErrorCount(0);
            realtimeEventsService.photoReady(photo);
            log.info("Media ready: photo {} ({})", photo.getId(), photo.getFileName());
        } catch (Exception e) {
            int errors = photo.getErrorCount() == null ? 1 : photo.getErrorCount() + 1;
            photo.setErrorCount(errors);
            if (errors >= MAX_ATTEMPTS) {
                photo.setStatus(PhotoStatus.ERROR);
                log.warn("Media processing failed permanently for photo {}: {}", photo.getId(), e.getMessage());
            } else {
                photo.setStatus(PhotoStatus.PROCESSING);
                mediaQueueService.enqueue(photoId);
                log.warn("Media processing failed for photo {} (attempt {}): {}", photo.getId(), errors, e.getMessage());
            }
        }
        photoRepository.save(photo);
    }
}