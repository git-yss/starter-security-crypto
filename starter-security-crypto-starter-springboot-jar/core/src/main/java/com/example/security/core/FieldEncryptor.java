package com.example.security.core;

public class FieldEncryptor {
    private final CryptoProvider cryptoProvider;

    public FieldEncryptor(CryptoProvider cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    public String encrypt(String value, EncryptField field) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("SC1$")) {
            return value;
        }
        return cryptoProvider.encrypt(field.algorithm(), value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new CryptoContext(field.keyVersion()));
    }

    public String decrypt(String value, EncryptField field) {
        if (value == null) {
            return null;
        }
        byte[] plaintext = cryptoProvider.decrypt(field.algorithm(), value,
                new CryptoContext(field.keyVersion()));
        return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    }
}
