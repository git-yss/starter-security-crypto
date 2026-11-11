package com.example.security.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryReplayStoreTest {
    @Test
    void acceptsNonceOnlyOnceWithinWindow() {
        InMemoryReplayStore store = new InMemoryReplayStore();
        assertTrue(store.useOnce("client:nonce", 60));
        assertFalse(store.useOnce("client:nonce", 60));
    }

    @Test
    void rejectsInvalidTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryReplayStore().useOnce("key", 0));
    }
}
