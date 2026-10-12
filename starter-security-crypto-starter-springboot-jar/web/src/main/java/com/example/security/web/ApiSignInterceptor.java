package com.example.security.web;

import com.example.security.core.ApiSign;
import com.example.security.core.AuditEvent;
import com.example.security.core.AuditPublisher;
import com.example.security.core.CryptoProvider;
import com.example.security.core.ReplayStore;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

public class ApiSignInterceptor implements HandlerInterceptor {
    public static final String TIMESTAMP = "X-Security-Timestamp";
    public static final String NONCE = "X-Security-Nonce";
    public static final String SIGNATURE = "X-Security-Signature";
    public static final String CLIENT_ID = "X-Security-Client-Id";

    private final CryptoProvider cryptoProvider;
    private final ReplayStore replayStore;
    private final AuditPublisher auditPublisher;
    private final long windowSeconds;
    private final String keyVersion;

    public ApiSignInterceptor(CryptoProvider cryptoProvider, ReplayStore replayStore,
                              AuditPublisher auditPublisher, long windowSeconds, String keyVersion) {
        this.cryptoProvider = cryptoProvider;
        this.replayStore = replayStore;
        this.auditPublisher = auditPublisher;
        this.windowSeconds = windowSeconds;
        this.keyVersion = keyVersion;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        ApiSign annotation = method.getMethodAnnotation(ApiSign.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(ApiSign.class);
        }
        if (annotation == null) {
            return true;
        }
        try {
            String timestampValue = request.getHeader(TIMESTAMP);
            String nonce = request.getHeader(NONCE);
            String signature = request.getHeader(SIGNATURE);
            String clientId = request.getHeader(CLIENT_ID);
            if (timestampValue == null || nonce == null || signature == null) {
                throw new IllegalArgumentException("missing signature headers");
            }
            long timestamp = Long.parseLong(timestampValue);
            long delta = Math.abs(System.currentTimeMillis() - timestamp);
            if (delta > windowSeconds * 1000L) {
                throw new IllegalArgumentException("request expired");
            }
            String canonical = canonical(request, annotation, timestampValue, nonce, clientId);
            if (!cryptoProvider.verify("HMAC_SHA256",
                    canonical.getBytes(StandardCharsets.UTF_8), signature, keyVersion)) {
                throw new IllegalArgumentException("invalid signature");
            }
            if (annotation.replayCheck()
                    && !replayStore.useOnce(clientId + ":" + nonce, windowSeconds)) {
                throw new IllegalArgumentException("replay request");
            }
            auditPublisher.publish(new AuditEvent("SIGN_VERIFY_SUCCESS", true));
            return true;
        } catch (Exception ex) {
            AuditEvent event = new AuditEvent("SIGN_VERIFY_FAIL", false);
            event.setFailureCode(ex.getMessage());
            event.setRequestUri(request.getRequestURI());
            auditPublisher.publish(event);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"success\":false,\"code\":\"SEC-4004\",\"message\":\"security validation failed\"}");
            } catch (java.io.IOException ignored) {
                // The response is already being rejected.
            }
            return false;
        }
    }

    public static String canonical(HttpServletRequest request, ApiSign annotation,
                                   String timestamp, String nonce, String clientId) {
        StringBuilder result = new StringBuilder();
        if (annotation.includeMethod()) {
            result.append(request.getMethod());
        }
        result.append('\n');
        if (annotation.includePath()) {
            result.append(request.getRequestURI());
        }
        result.append('\n');
        if (annotation.includeQuery() && request.getQueryString() != null) {
            result.append(request.getQueryString());
        }
        result.append('\n').append(timestamp).append('\n').append(nonce).append('\n')
                .append(clientId == null ? "" : clientId);
        return result.toString();
    }
}
