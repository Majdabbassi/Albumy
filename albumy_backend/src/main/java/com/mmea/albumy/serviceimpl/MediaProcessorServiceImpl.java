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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MediaProcessorServiceImpl {

    public static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(MediaProcessorServiceImpl.class);

    private final PhotoRepository photoRepository;
    private final MediaPipelineService mediaPipeline;
    private final MediaQueueService mediaQueueService;
    private final RealtimeEventsService realtimeEventsService;
    private final TransactionTemplate txTemplate;

    public MediaProcessorServiceImpl(PhotoRepository photoRepository,
                                     MediaPipelineService mediaPipeline,
                                     MediaQueueService mediaQueueService,
                                     RealtimeEventsService realtimeEventsService,
                                     PlatformTransactionManager txManager) {
        this.photoRepository = photoRepository;
        this.mediaPipeline = mediaPipeline;
        this.mediaQueueService = mediaQueueService;
        this.realtimeEventsService = realtimeEventsService;
        // The old @Transactional span pinned a DB connection for the whole ffmpeg/ImageIO
        // run; the template scopes a transaction to just the DB updates + realtime publish.
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public void process(Long photoId) {
        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null || photo.getStatus() == PhotoStatus.READY) {
            return;
        }
        try {
            mediaPipeline.process(photo);
        } catch (Exception e) {
            markFailed(photo, e);
            return;
        }
        photo.setStatus(PhotoStatus.READY);
        photo.setErrorCount(0);
        txTemplate.executeWithoutResult(s -> {
            photoRepository.save(photo);
            realtimeEventsService.photoReady(photo);
        });
        log.info("Media ready: photo {} ({})", photo.getId(), photo.getFileName());
    }

    private void markFailed(Photo photo, Exception e) {
        int errors = photo.getErrorCount() == null ? 1 : photo.getErrorCount() + 1;
        photo.setErrorCount(errors);
        if (errors >= MAX_ATTEMPTS) {
            txTemplate.executeWithoutResult(s -> {
                photo.setStatus(PhotoStatus.ERROR);
                photoRepository.save(photo);
            });
            log.warn("Media processing failed permanently for photo {}: {}", photo.getId(), e.getMessage());
        } else {
            txTemplate.executeWithoutResult(s -> {
                photo.setStatus(PhotoStatus.PROCESSING);
                photoRepository.save(photo);
            });
            mediaQueueService.enqueue(photo.getId(), photo.getMimeType());
            log.warn("Media processing failed for photo {} (attempt {}): {}", photo.getId(), errors, e.getMessage());
        }
    }
}