package com.example.security.core;

public interface AuditPublisher {
    void publish(AuditEvent event);
}
