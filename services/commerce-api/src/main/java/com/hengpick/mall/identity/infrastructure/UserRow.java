package com.hengpick.mall.identity.infrastructure;

public record UserRow(
        String id,
        String account,
        String displayName,
        String passwordHash,
        String role,
        String status) {}
