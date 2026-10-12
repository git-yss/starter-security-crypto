package com.example.security.autoconfigure;

import com.example.security.core.AuditPublisher;
import com.example.security.core.CryptoProvider;
import com.example.security.core.DefaultMasker;
import com.example.security.core.InMemoryReplayStore;
import com.example.security.core.KeyProvider;
import com.example.security.core.Masker;
import com.example.security.core.ReplayStore;
import com.example.security.core.JceCryptoProvider;
import com.example.security.core.FieldEncryptor;
import com.example.security.core.OperatorContextProvider;
import com.example.security.audit.AuditFilter;
import com.example.security.audit.DefaultOperatorContextProvider;
import com.example.security.web.ApiCryptoAdvice;
import com.example.security.web.ApiSignInterceptor;
import com.example.security.web.SensitiveModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnProperty(prefix = "security.crypto", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SecurityCryptoProperties.class)
public class SecurityCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyProvider securityKeyProvider() {
        return new DefaultKeyProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoProvider securityCryptoProvider(KeyProvider provider) {
        return new JceCryptoProvider(provider);
    }

    @Bean
    @ConditionalOnMissingBean
    public Masker securityMasker() {
        return new DefaultMasker();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditPublisher securityAuditPublisher() {
        return new Slf4jAuditPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplayStore securityReplayStore() {
        return new InMemoryReplayStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperatorContextProvider securityOperatorContextProvider() {
        return new DefaultOperatorContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldEncryptor securityFieldEncryptor(CryptoProvider provider) {
        return new FieldEncryptor(provider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.crypto.audit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AuditFilter> securityAuditFilter(
            AuditPublisher publisher,
            OperatorContextProvider operatorProvider,
            ObjectMapper objectMapper,
            SecurityCryptoProperties properties) {
        FilterRegistrationBean<AuditFilter> registration = new FilterRegistrationBean<AuditFilter>();
        registration.setFilter(new AuditFilter(publisher, operatorProvider, objectMapper,
                properties.getAudit().getMaxBodyLength()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MAX_VALUE - 10);
        return registration;
    }

    @Bean
    public SensitiveModule securitySensitiveModule(ObjectMapper objectMapper, Masker masker) {
        SensitiveModule module = new SensitiveModule(masker);
        objectMapper.registerModule(module);
        return module;
    }

    @Bean
    public ApiCryptoAdvice securityApiCryptoAdvice(ObjectMapper objectMapper,
                                                  CryptoProvider provider,
                                                  SecurityCryptoProperties properties) {
        return new ApiCryptoAdvice(objectMapper, provider, properties.getKeyVersion());
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.crypto.api-sign", name = "enabled", havingValue = "true")
    public ApiSignInterceptor securityApiSignInterceptor(CryptoProvider provider,
                                                        ReplayStore replayStore,
                                                        AuditPublisher auditPublisher,
                                                        SecurityCryptoProperties properties) {
        return new ApiSignInterceptor(provider, replayStore, auditPublisher,
                properties.getApiSign().getWindowSeconds(), properties.getKeyVersion());
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.crypto.api-sign", name = "enabled", havingValue = "true")
    public WebMvcConfigurer securityWebMvcConfigurer(final ApiSignInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
