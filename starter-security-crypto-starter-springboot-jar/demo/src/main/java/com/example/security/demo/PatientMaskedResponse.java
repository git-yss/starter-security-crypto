package com.example.security.demo;

import com.example.security.core.MaskType;
import com.example.security.core.Sensitive;

public class PatientMaskedResponse {
    private Long id;
    @Sensitive(type = MaskType.PHONE)
    private String phone;
    @Sensitive(type = MaskType.ADDRESS)
    private String address;
    @Sensitive(type = MaskType.ID_CARD)
    private String idCard;
    @Sensitive(type = MaskType.DEFAULT)
    private String infectionDiagnosis;

    public PatientMaskedResponse(PatientEntity patient) {
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
