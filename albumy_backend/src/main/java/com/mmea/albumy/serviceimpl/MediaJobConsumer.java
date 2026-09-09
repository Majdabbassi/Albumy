package com.mmea.albumy.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("worker")
@EnableScheduling
public class MediaJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(MediaJobConsumer.class);

    private final StringRedisTemplate redis;
    private final MediaProcessorServiceImpl processor;
    private final String queueKey;
    private final ExecutorService executor;
    private final AtomicInteger active = new AtomicInteger();

    public MediaJobConsumer(StringRedisTemplate redis,
                            MediaProcessorServiceImpl processor,
                            @Value("${media.queue:media:jobs}") String queueKey,
                            @Value("${media.jobs.threads:2}") int threads) {
        this.redis = redis;
        this.processor = processor;
        this.queueKey = queueKey;
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads));
    }

    @Scheduled(fixedDelay = 250)
    public void poll() {
        String job = redis.opsForList().rightPop(queueKey);
        while (job != null) {
            long photoId;
            try {
                photoId = Long.parseLong(job);
            } catch (NumberFormatException e) {
                job = redis.opsForList().rightPop(queueKey);
                continue;
            }
            active.incrementAndGet();
            executor.submit(() -> {
                try {
                    processor.process(photoId);
                } catch (Exception e) {
                    log.error("Unhandled error processing photo {}", photoId, e);
                } finally {
                    active.decrementAndGet();
                }
            });
            job = redis.opsForList().rightPop(queueKey);
        }
    }

    public int activeJobs() {
        return active.get();
    }
}