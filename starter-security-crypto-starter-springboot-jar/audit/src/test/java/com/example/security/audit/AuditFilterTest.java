package com.example.security.audit;

import com.example.security.core.AuditEvent;
import com.example.security.core.AuditPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditFilterTest {
    @Test
    void ignoresUntrustedIdentityHeadersAndRedactsSensitiveQueryValues() throws Exception {
        AtomicReference<AuditEvent> published = new AtomicReference<AuditEvent>();
        AuditFilter filter = filter(published::set, false, false, false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/patients");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Operator-Id", "forged-user");
        request.addHeader("X-Forwarded-For", "203.0.113.9");
        request.setQueryString("page=1&token=secret&phone=13812345678&pass%77ord=hidden");
        request.setContentType("application/json");
        request.setContent("{\"password\":\"secret\",\"name\":\"Alice\"}"
                .getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, new MockHttpServletResponse(), consumingChain());

        AuditEvent event = published.get();
        assertNull(event.getOperatorId());
        assertEquals("10.0.0.8", event.getClientIp());
        assertEquals("page=1&token=***&phone=***&pass%77ord=***", event.getQueryString());
        assertEquals("{\"password\":\"***\",\"name\":\"Alice\"}", event.getRequestBody());
    }

    @Test
    void auditPublishingFailureDoesNotBreakBusinessByDefault() {
        AuditFilter filter = filter(event -> { throw new IllegalStateException("audit unavailable"); },
                false, false, false);

        assertDoesNotThrow(() -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (request, response) -> { }));
    }

    @Test
    void canFailRequestWhenStrictAuditPublishingIsEnabled() {
        AuditFilter filter = filter(event -> { throw new IllegalStateException("audit unavailable"); },
                false, false, true);

        assertThrows(IllegalStateException.class, () -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (request, response) -> { }));
    }

    private AuditFilter filter(AuditPublisher publisher, boolean allowOperatorHeader,
                               boolean trustForwardedHeaders, boolean failOnPublishError) {
        return new AuditFilter(publisher, () -> null, new ObjectMapper(), 4096,
                allowOperatorHeader, trustForwardedHeaders, failOnPublishError);
    }

    private FilterChain consumingChain() {
        return new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
                byte[] buffer = new byte[128];
                while (request.getInputStream().read(buffer) >= 0) {
                    // 消费请求体，使 ContentCachingRequestWrapper 能够缓存读取到的内容。
                }
            }
        };
    }
}
