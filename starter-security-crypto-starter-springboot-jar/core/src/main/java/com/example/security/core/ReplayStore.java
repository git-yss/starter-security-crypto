package com.example.security.core;

public interface ReplayStore {
    boolean useOnce(String key, long ttlSeconds);
}
