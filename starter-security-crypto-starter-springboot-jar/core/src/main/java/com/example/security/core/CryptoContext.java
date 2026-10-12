package com.example.security.core;

import java.util.Collections;
import java.util.Map;

public class CryptoContext {
    private final String keyVersion;
    private final Map<String, String> attributes;

    public CryptoContext(String keyVersion) {
        this(keyVersion, Collections.<String, String>emptyMap());
    }

    public CryptoContext(String keyVersion, Map<String, String> attributes) {
        this.keyVersion = keyVersion;
        this.attributes = attributes == null ? Collections.<String, String>emptyMap() : attributes;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
