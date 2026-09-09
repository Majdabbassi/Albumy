package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.service.MediaQueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaQueueServiceImpl implements MediaQueueService {

    private final StringRedisTemplate redisTemplate;
    private final String queueKey;

    public MediaQueueServiceImpl(StringRedisTemplate redisTemplate,
                                 @Value("${media.queue:media:jobs}") String queueKey) {
        this.redisTemplate = redisTemplate;
        this.queueKey = queueKey;
    }

    @Override
    public void enqueue(Long photoId) {
        redisTemplate.opsForList().leftPush(queueKey, String.valueOf(photoId));
    }
}