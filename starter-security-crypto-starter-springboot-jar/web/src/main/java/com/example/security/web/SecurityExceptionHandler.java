package com.example.security.web;

import com.example.security.core.SecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class SecurityExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handle(SecurityException exception) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", Boolean.FALSE);
        body.put("code", exception.getCode());
        body.put("message", "security validation failed");
        return new ResponseEntity<Map<String, Object>>(body, HttpStatus.BAD_REQUEST);
    }
}
