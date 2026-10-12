package com.example.security.autoconfigure;

import com.example.security.core.KeyProvider;

import java.nio.charset.StandardCharsets;

public class DefaultKeyProvider implements KeyProvider {
    private final byte[] key;

    public DefaultKeyProvider() {
        this.key = "1234567890123456".getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getSymmetricKey(String keyVersion) {
        return key;
    }
}
