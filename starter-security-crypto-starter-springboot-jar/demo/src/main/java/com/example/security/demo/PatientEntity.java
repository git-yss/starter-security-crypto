package com.example.security.demo;

import com.example.security.core.EncryptField;

public class PatientEntity {
    private Long id;
    @EncryptField
    private String phone;
    @EncryptField
    private String address;
    @EncryptField
    private String idCard;
    @EncryptField
    private String infectionDiagnosis;

    public PatientEntity(Long id, String phone, String address, String idCard,
                         String infectionDiagnosis) {
        this.id = id;
        this.phone = phone;
        this.address = address;
        this.idCard = idCard;
        this.infectionDiagnosis = infectionDiagnosis;
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getIdCard() { return idCard; }
    public String getInfectionDiagnosis() { return infectionDiagnosis; }
}
