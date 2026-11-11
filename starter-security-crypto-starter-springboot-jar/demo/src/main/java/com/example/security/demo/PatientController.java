package com.example.security.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo/patients")
public class PatientController {
    @GetMapping("/{id}/masked")
    public PatientMaskedResponse masked(@PathVariable("id") Long id) {
        return new PatientMaskedResponse(patient(id));
    }

    /**
     * 本接口只用于演示返回明文数据。真实业务必须通过身份认证、数据权限以及必要的
     * 紧急授权审计机制保护此类接口。
     */
    @GetMapping("/{id}/plain")
    public PatientPlainResponse plain(@PathVariable("id") Long id) {
        return new PatientPlainResponse(patient(id));
    }

    private PatientEntity patient(Long id) {
        return new PatientEntity(id, "13812345678", "四川省成都市武侯区xxx",
                "510101199012345678", "特殊感染诊断记录");
    }
}
