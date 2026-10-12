package com.example.security.audit;

import com.example.security.core.AuditEvent;
import com.example.security.core.AuditPublisher;
import com.example.security.core.OperatorContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class AuditFilter extends OncePerRequestFilter {
    private final AuditPublisher publisher;
    private final OperatorContextProvider operatorProvider;
    private final ObjectMapper objectMapper;
    private final int maxBodyLength;

    public AuditFilter(AuditPublisher publisher, OperatorContextProvider operatorProvider,
                       ObjectMapper objectMapper, int maxBodyLength) {
        this.publisher = publisher;
        this.operatorProvider = operatorProvider;
        this.objectMapper = objectMapper;
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuditRequestWrapper wrapped = new AuditRequestWrapper(request);
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            AuditEvent event = new AuditEvent("HTTP_OPERATION", response.getStatus() < 400);
            String operatorId = operatorProvider.getOperatorId();
            event.setOperatorId(operatorId == null
                    ? request.getHeader("X-Operator-Id") : operatorId);
            event.setClientIp(clientIp(request));
            event.setServerHost(host());
            event.setHttpMethod(request.getMethod());
            event.setRequestUri(request.getRequestURI());
            event.setQueryString(request.getQueryString());
            event.setRequestBody(safeBody(wrapped));
            event.setResponseStatus(response.getStatus());
            event.setDurationMs(System.currentTimeMillis() - started);
            publisher.publish(event);
        }
    }

    private String safeBody(AuditRequestWrapper request) {
        byte[] bytes = request.getContentAsByteArray();
        if (bytes.length == 0) {
            return "";
        }
        int length = Math.min(bytes.length, maxBodyLength);
        String body = new String(bytes, 0, length, StandardCharsets.UTF_8);
        try {
            Object json = objectMapper.readValue(body, Object.class);
            redact(json);
            return objectMapper.writeValueAsString(json);
        } catch (Exception ignored) {
            return "[non-json-body]";
        }
    }

    private void redact(Object value) {
        if (value instanceof java.util.Collection) {
            for (Object item : (java.util.Collection<?>) value) {
                redact(item);
            }
            return;
        }
        if (!(value instanceof java.util.Map)) {
            return;
        }
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey()).toLowerCase();
            if (key.contains("password") || key.contains("token") || key.contains("secret")
                    || key.contains("authorization") || key.contains("idcard")
                    || key.contains("phone") || key.contains("mobile")
                    || key.contains("address") || key.contains("diagnosis")
                    || key.contains("infection") || key.contains("medical")) {
                ((java.util.Map) map).put(entry.getKey(), "***");
            } else {
                redact(entry.getValue());
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && forwarded.length() > 0) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
