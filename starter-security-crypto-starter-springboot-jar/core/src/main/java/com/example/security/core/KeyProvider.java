package com.example.security.core;

public interface KeyProvider {
    byte[] getSymmetricKey(String keyVersion);
}
