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
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 在 Controller 方法执行前校验标记了 {@link ApiSign} 的请求。
 *
 * <p>拦截器依次校验时间戳、构造规范签名原文、验证 HMAC，最后通过
 * {@link ReplayStore} 消费 nonce 以阻止重复请求。参与签名的原始请求体由
 * {@link SecurityRequestCachingFilter} 提前缓存。</p>
 */
public class ApiSignInterceptor implements HandlerInterceptor {
    public static final String TIMESTAMP = "X-Security-Timestamp";
    public static final String NONCE = "X-Security-Nonce";
    public static final String SIGNATURE = "X-Security-Signature";
    public static final String CLIENT_ID = "X-Security-Client-Id";
    public static final String KEY_VERSION = "X-Security-Key-Version";

    private final CryptoProvider cryptoProvider;
    private final ReplayStore replayStore;
    private final AuditPublisher auditPublisher;
    private final long signatureWindowSeconds;
    private final long replayWindowSeconds;
    private final String keyVersion;
    private final boolean replayEnabled;
    private final boolean failOnAuditPublishError;

    public ApiSignInterceptor(CryptoProvider cryptoProvider, ReplayStore replayStore,
                              AuditPublisher auditPublisher, long windowSeconds, String keyVersion,
                              boolean replayEnabled) {
        this(cryptoProvider, replayStore, auditPublisher, windowSeconds, keyVersion,
                windowSeconds, replayEnabled, false);
    }

    public ApiSignInterceptor(CryptoProvider cryptoProvider, ReplayStore replayStore,
                              AuditPublisher auditPublisher, long windowSeconds, String keyVersion,
                              long replayWindowSeconds, boolean replayEnabled,
                              boolean failOnAuditPublishError) {
        this.cryptoProvider = cryptoProvider;
        this.replayStore = replayStore;
        this.auditPublisher = auditPublisher;
        this.signatureWindowSeconds = windowSeconds;
        this.replayWindowSeconds = replayWindowSeconds;
        this.keyVersion = keyVersion;
        this.replayEnabled = replayEnabled;
        this.failOnAuditPublishError = failOnAuditPublishError;
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
            String requestKeyVersion = request.getHeader(KEY_VERSION);
            if (requestKeyVersion == null || requestKeyVersion.length() == 0) {
                requestKeyVersion = keyVersion;
            }
            if (timestampValue == null || nonce == null || signature == null
                    || clientId == null || clientId.length() == 0) {
                throw new IllegalArgumentException("missing signature headers");
            }
            if (nonce.length() < 16 || nonce.length() > 128 || clientId.length() > 128
                    || requestKeyVersion.length() > 64) {
                throw new IllegalArgumentException("invalid signature headers");
            }
            long timestamp = Long.parseLong(timestampValue);
            long now = System.currentTimeMillis();
            long windowMillis = signatureWindowSeconds * 1000L;
            if (timestamp < now - windowMillis || timestamp > now + windowMillis) {
                throw new IllegalArgumentException("request expired");
            }
            // 客户端与服务端必须使用完全相同的字段顺序和换行规则构造签名原文。
            String canonical = canonical(request, annotation, timestampValue, nonce, clientId,
                    requestKeyVersion);
            if (!cryptoProvider.verify("HMAC_SHA256",
                    canonical.getBytes(StandardCharsets.UTF_8), signature, requestKeyVersion)) {
                throw new IllegalArgumentException("invalid signature");
            }
            // 先验签再登记 nonce，避免攻击者用无效签名污染防重放存储。
            if (replayEnabled && annotation.replayCheck()
                    && !replayStore.useOnce(clientId + ":" + nonce, replayWindowSeconds)) {
                throw new IllegalArgumentException("replay request");
            }
            publishAudit(new AuditEvent("SIGN_VERIFY_SUCCESS", true));
            return true;
        } catch (Exception ex) {
            if (ex instanceof AuditPublishException) {
                throw ex;
            }
            AuditEvent event = new AuditEvent("SIGN_VERIFY_FAIL", false);
            event.setFailureCode(ex.getMessage());
            event.setRequestUri(request.getRequestURI());
            publishAudit(event);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"success\":false,\"code\":\"SEC-4004\",\"message\":\"security validation failed\"}");
            } catch (java.io.IOException ignored) {
                // 当前请求已经进入拒绝流程，写响应失败时不再覆盖原始安全校验结果。
            }
            return false;
        }
    }

    private void publishAudit(AuditEvent event) {
        try {
            auditPublisher.publish(event);
        } catch (RuntimeException ex) {
            if (failOnAuditPublishError) {
                throw new AuditPublishException(ex);
            }
        }
    }

    private static class AuditPublishException extends RuntimeException {
        private AuditPublishException(RuntimeException cause) {
            super(cause);
        }
    }

    public static String canonical(HttpServletRequest request, ApiSign annotation,
                                   String timestamp, String nonce, String clientId,
                                   String keyVersion) {
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
        result.append('\n');
        if (annotation.includeBody()) {
            result.append(bodyDigest(request));
        }
        result.append('\n').append(timestamp).append('\n').append(nonce).append('\n')
                .append(clientId).append('\n').append(keyVersion);
        return result.toString();
    }

    private static String bodyDigest(HttpServletRequest request) {
        byte[] body = cachedBody(request);
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static byte[] cachedBody(HttpServletRequest request) {
        ServletRequest current = request;
        // 请求可能被多个过滤器重复包装，因此需要沿包装器链向内查找缓存的原始请求体。
        while (current != null) {
            if (current instanceof CachedBodyHttpServletRequest) {
                return ((CachedBodyHttpServletRequest) current).getCachedBody();
            }
            if (!(current instanceof ServletRequestWrapper)) {
                break;
            }
            current = ((ServletRequestWrapper) current).getRequest();
        }
        return new byte[0];
    }
}
