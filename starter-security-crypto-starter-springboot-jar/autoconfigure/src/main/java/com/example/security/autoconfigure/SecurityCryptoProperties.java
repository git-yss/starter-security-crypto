package com.example.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.crypto")
public class SecurityCryptoProperties {
    private boolean enabled = true;
    private String environment = "dev";
    private String keyVersion = "V1";
    private String algorithm = "AES_GCM";
    private Replay replay = new Replay();
    private ApiSign apiSign = new ApiSign();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public Replay getReplay() { return replay; }
    public ApiSign getApiSign() { return apiSign; }

    public static class Replay {
        private boolean enabled;
        private long windowSeconds = 300;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    }

    public static class ApiSign {
        private boolean enabled;
        private long windowSeconds = 300;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
    }
}
