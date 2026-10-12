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
public class ApiCryptoAdvice implements RequestBodyAdvice, ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;
    private final CryptoProvider cryptoProvider;
    private final String keyVersion;

    public ApiCryptoAdvice(ObjectMapper objectMapper, CryptoProvider cryptoProvider, String keyVersion) {
        this.objectMapper = objectMapper;
        this.cryptoProvider = cryptoProvider;
        this.keyVersion = keyVersion;
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
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
