package com.example.security.autoconfigure;

import com.example.security.core.AuditEvent;
import com.example.security.core.AuditPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jAuditPublisher implements AuditPublisher {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public void publish(AuditEvent event) {
        log.info("auditId={},eventType={},success={},traceId={},requestUri={},failureCode={},timestamp={}",
                event.getTimestamp(), event.getEventType(), event.isSuccess(), event.getTraceId(),
                event.getRequestUri(), event.getFailureCode(), event.getTimestamp());
    }
}
