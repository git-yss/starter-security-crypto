package com.example.security.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiCrypto {
    ApiCryptoMode mode() default ApiCryptoMode.REQ_AND_RSP;
    String algorithm() default "AES_GCM";
    String requestField() default "data";
    String responseField() default "data";
}
