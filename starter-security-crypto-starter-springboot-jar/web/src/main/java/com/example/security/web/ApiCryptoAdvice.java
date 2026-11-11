package com.example.security.web;

import com.example.security.core.ApiCrypto;
import com.example.security.core.ApiCryptoMode;
import com.example.security.core.CryptoContext;
import com.example.security.core.CryptoProvider;
import com.example.security.core.SecurityException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
/**
 * 在 Spring MVC 消息转换前后处理标记了 {@link ApiCrypto} 的接口。
 *
 * <p>请求阶段先从加密信封中取出密文并解密成 JSON，再交给 Jackson 创建 Controller
 * 方法参数；响应阶段先把 Controller 返回对象序列化成 JSON，再加密并包装成响应信封。</p>
 */
public class ApiCryptoAdvice implements RequestBodyAdvice, ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;
    private final CryptoProvider cryptoProvider;
    private final String keyVersion;
    private final int maxBodySize;

    public ApiCryptoAdvice(ObjectMapper objectMapper, CryptoProvider cryptoProvider,
                           String keyVersion, int maxBodySize) {
        this.objectMapper = objectMapper;
        this.cryptoProvider = cryptoProvider;
        this.keyVersion = keyVersion;
        this.maxBodySize = maxBodySize;
    }

    @Override
    public boolean supports(MethodParameter parameter, java.lang.reflect.Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        ApiCrypto annotation = find(parameter);
        return annotation != null && annotation.mode() != ApiCryptoMode.NONE;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           java.lang.reflect.Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        ApiCrypto annotation = find(parameter);
        if (annotation == null || !requestEnabled(annotation)) {
            return inputMessage;
        }
        byte[] body = read(inputMessage.getBody());
        Map<String, Object> envelope = objectMapper.readValue(body,
                new TypeReference<Map<String, Object>>() { });
        Object value = envelope.get(annotation.requestField());
        if (!(value instanceof String)) {
            throw new SecurityException("SEC-4001", "Encrypted request field is missing");
        }
        byte[] plaintext = cryptoProvider.decrypt(annotation.algorithm(), (String) value,
                new CryptoContext(keyVersion));
        final HttpHeaders headers = new HttpHeaders();
        headers.putAll(inputMessage.getHeaders());
        headers.setContentLength(plaintext.length);
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(plaintext);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                java.lang.reflect.Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage,
                                  MethodParameter parameter, java.lang.reflect.Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        ApiCrypto annotation = find(returnType);
        return annotation != null && annotation.mode() != ApiCryptoMode.NONE;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        ApiCrypto annotation = find(returnType);
        if (annotation == null || !responseEnabled(annotation) || body == null) {
            return body;
        }
        try {
            String json = objectMapper.writeValueAsString(body);
            String encrypted = cryptoProvider.encrypt(annotation.algorithm(),
                    json.getBytes(StandardCharsets.UTF_8), new CryptoContext(keyVersion));
            Map<String, Object> envelope = new LinkedHashMap<String, Object>();
            envelope.put(annotation.responseField(), encrypted);
            return envelope;
        } catch (IOException ex) {
            throw new SecurityException("SEC-4003", "Response encryption failed", ex);
        }
    }

    private boolean requestEnabled(ApiCrypto annotation) {
        return annotation.mode() == ApiCryptoMode.REQ_AND_RSP
                || annotation.mode() == ApiCryptoMode.REQ_ONLY;
    }

    private boolean responseEnabled(ApiCrypto annotation) {
        return annotation.mode() == ApiCryptoMode.REQ_AND_RSP
                || annotation.mode() == ApiCryptoMode.RSP_ONLY;
    }

    private ApiCrypto find(MethodParameter parameter) {
        ApiCrypto method = parameter.getMethodAnnotation(ApiCrypto.class);
        if (method != null) {
            return method;
        }
        return parameter.getContainingClass().getAnnotation(ApiCrypto.class);
    }

    private byte[] read(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > maxBodySize) {
                throw new SecurityException("SEC-4010", "Request body is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
