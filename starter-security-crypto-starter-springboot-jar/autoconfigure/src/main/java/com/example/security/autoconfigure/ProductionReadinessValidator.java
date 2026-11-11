package com.example.security.autoconfigure;

import com.example.security.core.KeyProvider;
import org.springframework.beans.factory.InitializingBean;

public class ProductionReadinessValidator implements InitializingBean {
    private final SecurityCryptoProperties properties;
    private final KeyProvider keyProvider;

    public ProductionReadinessValidator(SecurityCryptoProperties properties, KeyProvider keyProvider) {
        this.properties = properties;
        this.keyProvider = keyProvider;
    }

    @Override
    public void afterPropertiesSet() {
        validateCommon();
        if (isProduction()) {
            validateProduction();
        }
    }

    private void validateCommon() {
        if (properties.getMaxBodySize() <= 0) {
            fail("security.crypto.max-body-size must be positive");
        }
        if (properties.getApiSign().getWindowSeconds() <= 0) {
            fail("API signature window must be positive");
        }
        if (properties.getApiSign().getWindowSeconds() > 86400) {
            fail("API signature window must not exceed 86400 seconds");
        }
        if (properties.getReplay().isEnabled() && properties.getReplay().getWindowSeconds() <= 0) {
            fail("Replay window must be positive");
        }
        if (properties.getAudit().getMaxBodyLength() <= 0) {
            fail("Audit max body length must be positive");
        }
        if (usesCrypto() && keyProvider.getSymmetricKey(properties.getKeyVersion()) == null) {
            fail("No symmetric key found for active key version " + properties.getKeyVersion());
        }
    }

    private void validateProduction() {
        if (keyProvider instanceof DefaultKeyProvider && !properties.getKey().isAllowLocalInProduction()) {
            fail("Local keys are disabled in production; provide an external KeyProvider");
        }
        if (properties.getApiSign().isEnabled() && properties.getReplay().isEnabled()
                && "memory".equalsIgnoreCase(properties.getReplay().getStore())) {
            fail("In-memory replay protection is not allowed in production");
        }
        if (properties.getAudit().isAllowOperatorHeader()) {
            fail("Client supplied operator headers are not allowed in production");
        }
    }

    private boolean isProduction() {
        return "prod".equalsIgnoreCase(properties.getEnvironment())
                || "production".equalsIgnoreCase(properties.getEnvironment());
    }

    private boolean usesCrypto() {
        return properties.getApiSign().isEnabled()
                || properties.getApiCrypto().isEnabled()
                || properties.getFieldEncrypt().isEnabled();
    }

    private void fail(String message) {
        if (properties.isFailFast() || isProduction()) {
            throw new IllegalStateException(message);
        }
    }
}
