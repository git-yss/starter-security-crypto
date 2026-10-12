package com.example.security.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReplayStore implements ReplayStore {
    private final Map<String, Long> entries = new ConcurrentHashMap<String, Long>();

    @Override
    public boolean useOnce(String key, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expiry = now + ttlSeconds * 1000L;
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
}
