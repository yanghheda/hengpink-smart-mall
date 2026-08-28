package com.hengpick.mall.identity.application;

public class ObjectAccessDeniedException extends RuntimeException {
    private final String code;

    public ObjectAccessDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
