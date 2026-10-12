package com.example.security.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiSign {
    boolean includeMethod() default true;
    boolean includePath() default true;
    boolean includeQuery() default true;
    boolean includeBody() default true;
    boolean replayCheck() default true;
}
