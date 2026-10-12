package com.example.security.demo;

import com.example.security.core.ApiCrypto;
import com.example.security.core.ApiSign;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/demo")
public class DemoController {

    @GetMapping("/users/{id}")
    public UserResponse getUser(@PathVariable("id") Long id) {
        return new UserResponse("杨顺", "13812345678", "510101199012345678");
    }

    @ApiCrypto
    @PostMapping("/secure/login")
    public Map<String, Object> login(@RequestBody SecureRequest request) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        result.put("username", request.getUsername());
        result.put("message", "login request was decrypted by ApiCrypto");
        return result;
    }

    @ApiSign
    @PostMapping("/signed/order")
    public Map<String, Object> signedOrder() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", Boolean.TRUE);
        result.put("message", "signature verified");
        return result;
    }
}
