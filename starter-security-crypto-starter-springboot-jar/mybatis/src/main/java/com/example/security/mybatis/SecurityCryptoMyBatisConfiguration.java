package com.example.security.mybatis;

import com.example.security.core.FieldEncryptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Interceptor.class)
@ConditionalOnProperty(prefix = "security.crypto.field-encrypt", name = "enabled",
        havingValue = "true")
public class SecurityCryptoMyBatisConfiguration {

    @Bean
    public Interceptor securityEncryptFieldInterceptor(FieldEncryptor fieldEncryptor) {
        return new EncryptFieldInterceptor(fieldEncryptor);
    }
}
