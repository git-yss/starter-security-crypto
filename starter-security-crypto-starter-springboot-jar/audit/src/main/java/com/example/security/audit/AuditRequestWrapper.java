package com.example.security.audit;

import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;

public class AuditRequestWrapper extends ContentCachingRequestWrapper {
    public AuditRequestWrapper(HttpServletRequest request) {
        super(request);
    }
}
