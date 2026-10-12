package com.example.security.demo;

public class PatientPlainResponse {
    private Long id;
    private String phone;
    private String address;
    private String idCard;
    private String infectionDiagnosis;

    public PatientPlainResponse(PatientEntity patient) {
        this.id = patient.getId();
        this.phone = patient.getPhone();
        this.address = patient.getAddress();
        this.idCard = patient.getIdCard();
        this.infectionDiagnosis = patient.getInfectionDiagnosis();
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getIdCard() { return idCard; }
    public String getInfectionDiagnosis() { return infectionDiagnosis; }
}
