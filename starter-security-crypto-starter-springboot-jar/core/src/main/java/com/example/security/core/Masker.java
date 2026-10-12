package com.example.security.core;

public interface Masker {
    String name();
    String mask(String value, MaskContext context);
}
