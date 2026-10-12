package com.example.security.core;

import java.util.regex.Pattern;

public class DefaultMasker implements Masker {
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern EMAIL = Pattern.compile("^([^@])[^@]*(@.+)$");

    @Override
    public String name() {
        return "DEFAULT";
    }

    @Override
    public String mask(String value, MaskContext context) {
        if (value == null || value.length() == 0) {
            return value;
        }
        MaskType type = context.getType();
        switch (type) {
            case PHONE:
                return value.length() <= 7 ? "****" : value.substring(0, 3) + "****"
                        + value.substring(value.length() - 4);
            case ID_CARD:
                return edge(value, 4, 4);
            case BANK_CARD:
                return edge(value, 4, 4);
            case EMAIL:
                return EMAIL.matcher(value).replaceFirst("$1***$2");
            case NAME:
                return value.length() <= 1 ? "*" : value.substring(0, 1) + repeat('*', value.length() - 1);
            case ADDRESS:
                return value.length() <= 4 ? "***" : value.substring(0, value.length() - 3) + "***";
            default:
                if (PHONE.matcher(value).matches()) {
                    return mask(value, new MaskContext(MaskType.PHONE));
                }
                return edge(value, 1, 1);
        }
    }

    private String edge(String value, int left, int right) {
        if (value.length() <= left + right) {
            return repeat('*', value.length());
        }
        return value.substring(0, left) + repeat('*', value.length() - left - right)
                + value.substring(value.length() - right);
    }

    private String repeat(char c, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(c);
        }
        return result.toString();
    }
}
