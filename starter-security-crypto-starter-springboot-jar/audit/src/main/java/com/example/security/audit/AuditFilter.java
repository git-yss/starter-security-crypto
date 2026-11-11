package com.example.security.audit;

import com.example.security.core.AuditEvent;
import com.example.security.core.AuditPublisher;
import com.example.security.core.OperatorContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 在下游过滤器和业务接口执行完成后记录一次 HTTP 操作审计。
 *
 * <p>客户端可以伪造身份相关请求头，因此默认不信任这些请求头。查询参数和 JSON
 * 请求体会先完成敏感信息脱敏，再通过 {@link AuditPublisher} 发布审计事件。</p>
 */
public class AuditFilter extends OncePerRequestFilter {
    private static final Log LOG = LogFactory.getLog(AuditFilter.class);

    private final AuditPublisher publisher;
    private final OperatorContextProvider operatorProvider;
    private final ObjectMapper objectMapper;
    private final int maxBodyLength;
    private final boolean allowOperatorHeader;
    private final boolean trustForwardedHeaders;
    private final boolean failOnPublishError;

    public AuditFilter(AuditPublisher publisher, OperatorContextProvider operatorProvider,
                       ObjectMapper objectMapper, int maxBodyLength,
                       boolean allowOperatorHeader, boolean trustForwardedHeaders,
                       boolean failOnPublishError) {
        this.publisher = publisher;
        this.operatorProvider = operatorProvider;
        this.objectMapper = objectMapper;
        this.maxBodyLength = maxBodyLength;
        this.allowOperatorHeader = allowOperatorHeader;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.failOnPublishError = failOnPublishError;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuditRequestWrapper wrapped = new AuditRequestWrapper(request);
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            // 使用 finally 可以同时记录正常响应和下游执行过程中发生异常的请求。
            AuditEvent event = new AuditEvent("HTTP_OPERATION", response.getStatus() < 400);
            String operatorId = operatorProvider.getOperatorId();
            event.setOperatorId(operatorId == null && allowOperatorHeader
                    ? request.getHeader("X-Operator-Id") : operatorId);
            event.setClientIp(clientIp(request));
            event.setServerHost(host());
            event.setHttpMethod(request.getMethod());
            event.setRequestUri(request.getRequestURI());
            event.setQueryString(safeQuery(request.getQueryString()));
            event.setRequestBody(safeBody(wrapped));
            event.setResponseStatus(response.getStatus());
            event.setDurationMs(System.currentTimeMillis() - started);
            publish(event);
        }
    }

    private void publish(AuditEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException ex) {
            if (failOnPublishError) {
                throw ex;
            }
            LOG.error("Failed to publish security audit event", ex);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
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
            if (isSensitiveKey(key)) {
                ((java.util.Map) map).put(entry.getKey(), "***");
            } else {
                redact(entry.getValue());
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = trustForwardedHeaders ? request.getHeader("X-Forwarded-For") : null;
        if (forwarded != null && forwarded.length() > 0) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String safeQuery(String query) {
        if (query == null || query.length() == 0) {
            return query;
        }
        StringBuilder result = new StringBuilder();
        String[] pairs = query.split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                result.append('&');
            }
            String pair = pairs[i];
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            result.append(key);
            if (separator >= 0) {
                result.append('=').append(isSensitiveKey(normalizeQueryKey(key))
                        ? "***" : pair.substring(separator + 1));
            }
        }
        return result.toString();
    }

    private String normalizeQueryKey(String key) {
        try {
            return URLDecoder.decode(key, "UTF-8").toLowerCase();
        } catch (Exception ignored) {
            return key.toLowerCase();
        }
    }

    private boolean isSensitiveKey(String key) {
        return key.contains("password") || key.contains("token") || key.contains("secret")
                || key.contains("authorization") || key.contains("idcard")
                || key.contains("phone") || key.contains("mobile")
                || key.contains("address") || key.contains("diagnosis")
                || key.contains("infection") || key.contains("medical");
    }

    private String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}
