package com.example.security.autoconfigure;

import com.example.security.core.ReplayStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisReplayStore implements ReplayStore {
    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisReplayStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean useOnce(String key, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(
                keyPrefix + key, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(stored);
    }
}
