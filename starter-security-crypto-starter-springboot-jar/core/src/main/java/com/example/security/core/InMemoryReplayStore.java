package com.example.security.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReplayStore implements ReplayStore {
    private final Map<String, Long> entries = new ConcurrentHashMap<String, Long>();
    private static final int CLEANUP_INTERVAL = 256;
    private final java.util.concurrent.atomic.AtomicLong operations =
            new java.util.concurrent.atomic.AtomicLong();

    @Override
    public boolean useOnce(String key, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        long now = System.currentTimeMillis();
        long expiry = now + ttlSeconds * 1000L;
        if (operations.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanup(now);
        }
        while (true) {
            Long old = entries.get(key);
            if (old != null && old > now) {
                return false;
            }
            if (old == null) {
                if (entries.putIfAbsent(key, expiry) == null) {
                    return true;
                }
            } else if (entries.replace(key, old, expiry)) {
                return true;
            }
        }
    }

    private void cleanup(long now) {
        for (Map.Entry<String, Long> entry : entries.entrySet()) {
            Long expiry = entry.getValue();
            if (expiry != null && expiry <= now) {
                entries.remove(entry.getKey(), expiry);
            }
        }
    }
}
