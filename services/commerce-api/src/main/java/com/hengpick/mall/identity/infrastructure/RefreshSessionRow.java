package com.hengpick.mall.identity.infrastructure;

public record RefreshSessionRow(
        String sessionId,
        String refreshTokenHash,
        String userId,
        String account,
        String displayName,
        String passwordHash,
        String role,
        String userStatus) {}
