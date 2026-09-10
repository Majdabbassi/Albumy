package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.repository.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("worker")
@EnableScheduling
public class MediaJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(MediaJobConsumer.class);

    private final StringRedisTemplate redis;
    private final MediaProcessorServiceImpl processor;
    private final PhotoRepository photoRepository;
    private final String imageQueueKey;
    private final String videoQueueKey;
    private final int threads;
    private final int queueCapacity;
    private final Duration recoverAfter;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger pending = new AtomicInteger();
    private final Set<Long> processing = ConcurrentHashMap.newKeySet();

    public MediaJobConsumer(StringRedisTemplate redis,
                            MediaProcessorServiceImpl processor,
                            PhotoRepository photoRepository,
                            @Value("${media.queue:media:jobs}") String queueKey,
                            @Value("${media.jobs.threads:2}") int threads,
                            @Value("${media.jobs.queue:16}") int queueCapacity,
                            @Value("${media.jobs.recover.minutes:30}") int recoverMinutes) {
        this.redis = redis;
        this.processor = processor;
        this.photoRepository = photoRepository;
        this.imageQueueKey = queueKey;
        this.videoQueueKey = queueKey + ":video";
        this.threads = Math.max(1, threads);
        this.queueCapacity = Math.max(1, queueCapacity);
        this.recoverAfter = Duration.ofMinutes(recoverMinutes);
        this.executor = new ThreadPoolExecutor(
                this.threads, this.threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(this.queueCapacity));
    }

    /**
     * Pulls jobs off Redis only as fast as the bounded executor can absorb them.
     * Jobs that are not pulled (or that get rejected) stay in Redis, so a crash or
     * restart can never lose queued work.
     */
    @Scheduled(fixedDelay = 250)
    public void poll() {
        while (true) {
            String sourceKey = imageQueueKey;
            String job = redis.opsForList().rightPop(imageQueueKey);
            if (job == null) {
                job = redis.opsForList().rightPop(videoQueueKey);
                sourceKey = videoQueueKey;
            }
            if (job == null) {
                return;
            }
            long photoId;
            try {
                photoId = Long.parseLong(job);
            } catch (NumberFormatException e) {
                continue;
            }
            if (!submit(photoId)) {
                redis.opsForList().leftPush(sourceKey, job);
                return;
            }
        }
    }

    /**
     * Re-enqueues photos that have been stuck in PROCESSING past the recovery
     * window (e.g. the worker was down). Processing is guarded by the in-memory
     * set so an actively-transcoding photo is never duplicated.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void recoverStuckJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minus(recoverAfter);
        List<Photo> stale = photoRepository.findByStatusAndUploadedAtBefore(PhotoStatus.PROCESSING, cutoff);
        for (Photo photo : stale) {
            if (processing.contains(photo.getId())) {
                continue;
            }
            if (photo.getErrorCount() != null && photo.getErrorCount() >= MediaProcessorServiceImpl.MAX_ATTEMPTS) {
                continue;
            }
            redis.opsForList().leftPush(
                    photo.getMimeType() != null && photo.getMimeType().startsWith("video/") ? videoQueueKey : imageQueueKey,
                    String.valueOf(photo.getId()));
            log.info("Re-enqueued stuck PROCESSING photo {}", photo.getId());
        }
    }

    private boolean submit(long photoId) {
        if (pending.get() >= threads + queueCapacity) {
            return false;
        }
        pending.incrementAndGet();
        processing.add(photoId);
        try {
            executor.execute(() -> {
                try {
                    processor.process(photoId);
                } catch (Exception e) {
                    log.error("Unhandled error processing photo {}", photoId, e);
                } finally {
                    processing.remove(photoId);
                    pending.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            processing.remove(photoId);
            pending.decrementAndGet();
            return false;
        }
    }
}