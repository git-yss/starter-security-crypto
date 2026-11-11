package com.example.security.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JceCryptoProviderTest {
    @Test
    void doesNotPretendSha256IsSm3() {
        JceCryptoProvider provider = new JceCryptoProvider(
                version -> "1234567890123456".getBytes(StandardCharsets.UTF_8));

        assertThrows(SecurityException.class,
                () -> provider.digest("SM3", "data".getBytes(StandardCharsets.UTF_8)));
    }
    @Test
    void decryptsWithVersionEmbeddedInCiphertext() {
        Map<String, byte[]> keys = new HashMap<String, byte[]>();
        keys.put("V1", "1234567890123456".getBytes(StandardCharsets.UTF_8));
        keys.put("V2", "6543210987654321".getBytes(StandardCharsets.UTF_8));
        JceCryptoProvider provider = new JceCryptoProvider(keys::get);

        byte[] plaintext = "patient-data".getBytes(StandardCharsets.UTF_8);
        String ciphertext = provider.encrypt("AES_GCM", plaintext, new CryptoContext("V1"));

        assertTrue(ciphertext.startsWith("SC1$AES_GCM$V1$"));
        assertArrayEquals(plaintext,
                provider.decrypt("AES_GCM", ciphertext, new CryptoContext("V2")));
    }

    @Test
    void usesDifferentKeysForDifferentVersions() {
        Map<String, byte[]> keys = new HashMap<String, byte[]>();
        keys.put("V1", "1234567890123456".getBytes(StandardCharsets.UTF_8));
        keys.put("V2", "6543210987654321".getBytes(StandardCharsets.UTF_8));
        JceCryptoProvider provider = new JceCryptoProvider(keys::get);

        String v1 = provider.encrypt("AES_GCM", new byte[] {1}, new CryptoContext("V1"));
        String v2 = provider.encrypt("AES_GCM", new byte[] {1}, new CryptoContext("V2"));

        assertNotEquals(v1, v2);
        assertThrows(SecurityException.class,
                () -> provider.sign("UNKNOWN", new byte[] {1}, "V1"));
    }
}
