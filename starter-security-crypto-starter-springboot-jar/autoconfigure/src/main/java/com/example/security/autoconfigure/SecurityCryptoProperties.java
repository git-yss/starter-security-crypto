package com.example.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "security.crypto")
public class SecurityCryptoProperties {
    private boolean enabled = true;
    private String environment = "dev";
    private boolean failFast = true;
    private String keyVersion = "V1";
    private int maxBodySize = 1024 * 1024;
    private Replay replay = new Replay();
    private ApiSign apiSign = new ApiSign();
    private ApiCrypto apiCrypto = new ApiCrypto();
    private Audit audit = new Audit();
    private FieldEncrypt fieldEncrypt = new FieldEncrypt();
    private Key key = new Key();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public boolean isFailFast() { return failFast; }
    public void setFailFast(boolean failFast) { this.failFast = failFast; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public int getMaxBodySize() { return maxBodySize; }
    public void setMaxBodySize(int maxBodySize) { this.maxBodySize = maxBodySize; }
    public Replay getReplay() { return replay; }
    public ApiSign getApiSign() { return apiSign; }
    public ApiCrypto getApiCrypto() { return apiCrypto; }
    public Audit getAudit() { return audit; }
    public FieldEncrypt getFieldEncrypt() { return fieldEncrypt; }
    public Key getKey() { return key; }

    public static class Replay {
        private boolean enabled;
        private long windowSeconds = 300;
        private String store = "memory";
        private String keyPrefix = "security:replay:";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
        public String getStore() { return store; }
        public void setStore(String store) { this.store = store; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    }

    public static class ApiSign {
        private boolean enabled;
        private long windowSeconds = 300;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    }

    public static class ApiCrypto {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Audit {
        private boolean enabled = true;
        private int maxBodyLength = 4096;
        private boolean allowOperatorHeader;
        private boolean trustForwardedHeaders;
        private boolean failOnPublishError;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxBodyLength() { return maxBodyLength; }
        public void setMaxBodyLength(int maxBodyLength) { this.maxBodyLength = maxBodyLength; }
        public boolean isAllowOperatorHeader() { return allowOperatorHeader; }
        public void setAllowOperatorHeader(boolean allowOperatorHeader) { this.allowOperatorHeader = allowOperatorHeader; }
        public boolean isTrustForwardedHeaders() { return trustForwardedHeaders; }
        public void setTrustForwardedHeaders(boolean trustForwardedHeaders) { this.trustForwardedHeaders = trustForwardedHeaders; }
        public boolean isFailOnPublishError() { return failOnPublishError; }
        public void setFailOnPublishError(boolean failOnPublishError) { this.failOnPublishError = failOnPublishError; }
    }

    public static class FieldEncrypt {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Key {
        private boolean allowLocalInProduction;
        private Map<String, String> local = new LinkedHashMap<String, String>();

        public boolean isAllowLocalInProduction() { return allowLocalInProduction; }
        public void setAllowLocalInProduction(boolean allowLocalInProduction) { this.allowLocalInProduction = allowLocalInProduction; }
        public Map<String, String> getLocal() { return local; }
        public void setLocal(Map<String, String> local) {
            this.local = local == null ? new LinkedHashMap<String, String>() : local;
        }
    }
}
