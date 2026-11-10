package com.example.security.core;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class JceCryptoProvider implements CryptoProvider {
    private static final String PREFIX = "SC1$AES_GCM$";
    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public JceCryptoProvider(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public String encrypt(String algorithm, byte[] plaintext, CryptoContext context) {
        if (!"AES_GCM".equalsIgnoreCase(algorithm)) {
            throw new SecurityException("SEC-4009", "Unsupported encryption algorithm");
        }
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(context.getKeyVersion()), "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            return PREFIX + context.getKeyVersion() + "$"
                    + Base64.getEncoder().encodeToString(iv) + "$"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new SecurityException("SEC-4003", "Encryption failed", ex);
        }
    }

    @Override
    public byte[] decrypt(String algorithm, String ciphertext, CryptoContext context) {
        if (!"AES_GCM".equalsIgnoreCase(algorithm) || ciphertext == null
                || !ciphertext.startsWith(PREFIX)) {
            throw new SecurityException("SEC-4001", "Invalid ciphertext");
        }
        try {
            String[] parts = ciphertext.split("\\$", -1);
            if (parts.length != 5) {
                throw new SecurityException("SEC-4001", "Invalid ciphertext format");
            }
            String keyVersion = parts[2];
            if (keyVersion.length() == 0) {
                throw new SecurityException("SEC-4001", "Ciphertext key version is missing");
            }
            byte[] iv = Base64.getDecoder().decode(parts[3]);
            if (iv.length != 12) {
                throw new SecurityException("SEC-4001", "Invalid GCM IV length");
            }
            byte[] encrypted = Base64.getDecoder().decode(parts[4]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(keyVersion), "AES"),
                    new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (SecurityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SecurityException("SEC-4002", "Decryption failed", ex);
        }
    }

    @Override
    public byte[] digest(String algorithm, byte[] data) {
        if (!"SHA-256".equalsIgnoreCase(algorithm) && !"SHA256".equalsIgnoreCase(algorithm)) {
            throw new SecurityException("SEC-4009", "Unsupported digest algorithm");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception ex) {
            throw new SecurityException("SEC-4009", "Unsupported digest algorithm", ex);
        }
    }

    @Override
    public String sign(String algorithm, byte[] data, String keyVersion) {
        requireHmacSha256(algorithm);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(keyVersion), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (Exception ex) {
            throw new SecurityException("SEC-5001", "Signing failed", ex);
        }
    }

    @Override
    public boolean verify(String algorithm, byte[] data, String signature, String keyVersion) {
        requireHmacSha256(algorithm);
        if (signature == null) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(sign(algorithm, data, keyVersion));
        byte[] actual;
        try {
            actual = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] key(String keyVersion) {
        if (keyVersion == null || keyVersion.length() == 0) {
            throw new SecurityException("SEC-4008", "Key version is required");
        }
        byte[] key = keyProvider.getSymmetricKey(keyVersion);
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new SecurityException("SEC-4008", "Invalid symmetric key");
        }
        return key;
    }

    private void requireHmacSha256(String algorithm) {
        if (!"HMAC_SHA256".equalsIgnoreCase(algorithm)) {
            throw new SecurityException("SEC-5002", "Unsupported signature algorithm");
        }
    }
}
