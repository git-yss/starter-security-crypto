package com.example.security.autoconfigure;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionReadinessValidatorTest {
    @Test
    void rejectsLocalKeysInProduction() {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.setEnvironment("prod");
        properties.getApiCrypto().setEnabled(true);
        properties.getKey().setLocal(Collections.singletonMap(
                "V1", "MTIzNDU2Nzg5MDEyMzQ1Ng=="));
        DefaultKeyProvider provider = new DefaultKeyProvider(properties.getKey().getLocal());

        assertThrows(IllegalStateException.class,
                () -> new ProductionReadinessValidator(properties, provider).afterPropertiesSet());
    }

    @Test
    void rejectsMemoryReplayStoreConfigurationInProduction() {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.setEnvironment("prod");
        properties.getApiSign().setEnabled(true);
        properties.getReplay().setEnabled(true);
        properties.getReplay().setStore("memory");

        assertThrows(IllegalStateException.class,
                () -> new ProductionReadinessValidator(properties,
                        version -> "1234567890123456".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .afterPropertiesSet());
    }
}
