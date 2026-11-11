package com.example.security.web;

import com.example.security.core.ApiSign;
import com.example.security.core.AuditEvent;
import com.example.security.core.AuditPublisher;
import com.example.security.core.InMemoryReplayStore;
import com.example.security.core.JceCryptoProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSignInterceptorTest {
    private final byte[] key = "1234567890123456".getBytes(StandardCharsets.UTF_8);
    private final JceCryptoProvider cryptoProvider = new JceCryptoProvider(version -> key);

    @Test
    void rejectsBodyTampering() throws Exception {
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest base = signedRequest("{\"amount\":100}", timestamp);
        CachedBodyHttpServletRequest original = new CachedBodyHttpServletRequest(
                base, "{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
        ApiSign annotation = handler().getMethodAnnotation(ApiSign.class);
        String canonical = ApiSignInterceptor.canonical(original, annotation,
                String.valueOf(timestamp), "1234567890abcdef", "client-a", "V1");
        base.addHeader(ApiSignInterceptor.SIGNATURE, cryptoProvider.sign("HMAC_SHA256",
                canonical.getBytes(StandardCharsets.UTF_8), "V1"));

        CachedBodyHttpServletRequest tampered = new CachedBodyHttpServletRequest(
                base, "{\"amount\":999}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor().preHandle(tampered, response, handler()));
    }

    @Test
    void acceptsValidSignature() throws Exception {
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest base = signedRequest("{}", timestamp);
        CachedBodyHttpServletRequest request = new CachedBodyHttpServletRequest(
                base, "{}".getBytes(StandardCharsets.UTF_8));
        ApiSign annotation = handler().getMethodAnnotation(ApiSign.class);
        String canonical = ApiSignInterceptor.canonical(request, annotation,
                String.valueOf(timestamp), "1234567890abcdef", "client-a", "V1");
        base.addHeader(ApiSignInterceptor.SIGNATURE, cryptoProvider.sign("HMAC_SHA256",
                canonical.getBytes(StandardCharsets.UTF_8), "V1"));

        assertTrue(interceptor().preHandle(request, new MockHttpServletResponse(), handler()));
    }

    @Test
    void includesBodyThroughNestedRequestWrappers() throws Exception {
        long timestamp = System.currentTimeMillis();
        MockHttpServletRequest base = signedRequest("{}", timestamp);
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(
                base, "{}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper outer = new ContentCachingRequestWrapper(cached);
        ApiSign annotation = handler().getMethodAnnotation(ApiSign.class);

        String direct = ApiSignInterceptor.canonical(cached, annotation,
                String.valueOf(timestamp), "1234567890abcdef", "client-a", "V1");
        String nested = ApiSignInterceptor.canonical(outer, annotation,
                String.valueOf(timestamp), "1234567890abcdef", "client-a", "V1");

        org.junit.jupiter.api.Assertions.assertEquals(direct, nested);
    }

    private MockHttpServletRequest signedRequest(String body, long timestamp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(ApiSignInterceptor.TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(ApiSignInterceptor.NONCE, "1234567890abcdef");
        request.addHeader(ApiSignInterceptor.CLIENT_ID, "client-a");
        request.addHeader(ApiSignInterceptor.KEY_VERSION, "V1");
        return request;
    }

    private ApiSignInterceptor interceptor() {
        return new ApiSignInterceptor(cryptoProvider, new InMemoryReplayStore(),
                new AuditPublisher() {
                    @Override
                    public void publish(AuditEvent event) {
                    }
                }, 300, "V1", false);
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("create");
        return new HandlerMethod(new TestController(), method);
    }

    private static class TestController {
        @ApiSign
        public void create() {
        }
    }
}
