package com.example.security.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
    MaskType type() default MaskType.DEFAULT;
    String strategy() default "";
    boolean ignore() default false;
}
