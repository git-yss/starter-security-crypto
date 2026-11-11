package com.example.security.autoconfigure;

import com.example.security.core.ReplayStore;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@AutoConfigureAfter(SecurityCryptoAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "security.crypto", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SecurityCryptoProperties.class)
public class RedisReplayStoreAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ReplayStore.class)
    @ConditionalOnProperty(prefix = "security.crypto.replay", name = "store", havingValue = "redis")
    public ReplayStore securityRedisReplayStore(StringRedisTemplate redisTemplate,
                                                SecurityCryptoProperties properties) {
        return new RedisReplayStore(redisTemplate, properties.getReplay().getKeyPrefix());
    }
}
