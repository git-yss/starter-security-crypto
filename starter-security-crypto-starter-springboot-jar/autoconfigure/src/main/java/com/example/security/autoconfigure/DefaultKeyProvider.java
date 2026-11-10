package com.example.security.autoconfigure;

import com.example.security.core.KeyProvider;
import com.example.security.core.SecurityException;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultKeyProvider implements KeyProvider {
    private final Map<String, byte[]> keys;

    public DefaultKeyProvider(Map<String, String> encodedKeys) {
        Map<String, byte[]> decoded = new LinkedHashMap<String, byte[]>();
        if (encodedKeys != null) {
            for (Map.Entry<String, String> entry : encodedKeys.entrySet()) {
                try {
                    decoded.put(entry.getKey(), Base64.getDecoder().decode(entry.getValue()));
                } catch (IllegalArgumentException ex) {
                    throw new SecurityException("SEC-4008", "Invalid Base64 key for version " + entry.getKey(), ex);
                }
            }
        }
        this.keys = Collections.unmodifiableMap(decoded);
    }

    @Override
    public byte[] getSymmetricKey(String keyVersion) {
        byte[] key = keys.get(keyVersion);
        return key == null ? null : key.clone();
    }
}
