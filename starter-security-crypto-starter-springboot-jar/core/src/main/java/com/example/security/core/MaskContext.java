package com.example.security.core;

public class MaskContext {
    private final MaskType type;

    public MaskContext(MaskType type) {
        this.type = type;
    }

    public MaskType getType() {
        return type;
    }
}
