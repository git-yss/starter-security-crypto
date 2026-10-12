package com.example.security.demo;

import com.example.security.core.Sensitive;
import com.example.security.core.MaskType;

public class UserResponse {
    private String name;
    @Sensitive(type = MaskType.PHONE)
    private String phone;
    @Sensitive(type = MaskType.ID_CARD)
    private String idCard;

    public UserResponse(String name, String phone, String idCard) {
        this.name = name;
        this.phone = phone;
        this.idCard = idCard;
    }

    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getIdCard() { return idCard; }
}
