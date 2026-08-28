package com.hengpick.mall.identity.domain;

public record UserAccount(
        String id,
        String account,
        String displayName,
        String passwordHash,
        String role,
        String status) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
