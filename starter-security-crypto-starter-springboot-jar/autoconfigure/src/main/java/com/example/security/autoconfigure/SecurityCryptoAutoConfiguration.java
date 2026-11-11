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
import com.example.security.web.SecurityExceptionHandler;
import com.example.security.web.SecurityRequestCachingFilter;
import com.example.security.web.SensitiveModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnProperty(prefix = "security.crypto", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SecurityCryptoProperties.class)
/**
 * Starter 的主自动配置入口。
 *
 * <p>Spring Boot 通过 {@code META-INF/spring.factories} 加载本类。每个 Bean 方法
 * 都负责把 {@code core} 模块中定义的扩展接口连接到默认实现。大部分默认 Bean
 * 使用 {@code ConditionalOnMissingBean}，因此业务项目可以通过声明同类型 Bean
 * 替换默认实现，而不需要修改 Starter 源码。</p>
 */
public class SecurityCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyProvider securityKeyProvider(SecurityCryptoProperties properties) {
        return new DefaultKeyProvider(properties.getKey().getLocal());
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
    @ConditionalOnProperty(prefix = "security.crypto.replay", name = "store",
            havingValue = "memory", matchIfMissing = true)
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
                properties.getAudit().getMaxBodyLength(),
                properties.getAudit().isAllowOperatorHeader(),
                properties.getAudit().isTrustForwardedHeaders(),
                properties.getAudit().isFailOnPublishError()));
        registration.addUrlPatterns("/*");
        // 审计过滤器在请求体缓存过滤器之后执行，确保两层请求包装器能够同时生效。
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.crypto.api-sign", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<SecurityRequestCachingFilter> securityRequestCachingFilter(
            SecurityCryptoProperties properties) {
        FilterRegistrationBean<SecurityRequestCachingFilter> registration =
                new FilterRegistrationBean<SecurityRequestCachingFilter>();
        registration.setFilter(new SecurityRequestCachingFilter(properties.getMaxBodySize()));
        registration.addUrlPatterns("/*");
        // MVC 调用 ApiSignInterceptor 验签前，必须先缓存只能读取一次的请求体。
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public SensitiveModule securitySensitiveModule(ObjectMapper objectMapper, Masker masker) {
        SensitiveModule module = new SensitiveModule(masker);
        objectMapper.registerModule(module);
        return module;
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.crypto.api-crypto", name = "enabled", havingValue = "true")
    public ApiCryptoAdvice securityApiCryptoAdvice(ObjectMapper objectMapper,
                                                  CryptoProvider provider,
                                                  SecurityCryptoProperties properties) {
        return new ApiCryptoAdvice(objectMapper, provider, properties.getKeyVersion(),
                properties.getMaxBodySize());
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    public ProductionReadinessValidator securityProductionReadinessValidator(
            SecurityCryptoProperties properties, KeyProvider keyProvider) {
        return new ProductionReadinessValidator(properties, keyProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "security.crypto.api-sign", name = "enabled", havingValue = "true")
    public ApiSignInterceptor securityApiSignInterceptor(CryptoProvider provider,
                                                        ReplayStore replayStore,
                                                        AuditPublisher auditPublisher,
                                                        SecurityCryptoProperties properties) {
        return new ApiSignInterceptor(provider, replayStore, auditPublisher,
                properties.getApiSign().getWindowSeconds(), properties.getKeyVersion(),
                properties.getReplay().getWindowSeconds(),
                properties.getReplay().isEnabled(),
                properties.getAudit().isFailOnPublishError());
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
