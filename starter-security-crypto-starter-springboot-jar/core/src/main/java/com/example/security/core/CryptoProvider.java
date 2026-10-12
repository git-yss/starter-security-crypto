package com.example.security.core;

public interface CryptoProvider {
    String encrypt(String algorithm, byte[] plaintext, CryptoContext context);
    byte[] decrypt(String algorithm, String ciphertext, CryptoContext context);
    byte[] digest(String algorithm, byte[] data);
    String sign(String algorithm, byte[] data, String keyVersion);
    boolean verify(String algorithm, byte[] data, String signature, String keyVersion);
}
