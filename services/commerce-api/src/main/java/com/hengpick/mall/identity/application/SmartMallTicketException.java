package com.hengpick.mall.identity.application;

public class SmartMallTicketException extends RuntimeException {
    private final String code;

    public SmartMallTicketException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
