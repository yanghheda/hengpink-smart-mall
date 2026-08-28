package com.hengpick.mall.identity.domain;

import java.time.Instant;

public record AuthSession(
        String id,
        String userId,
        String deviceSessionId,
        String refreshTokenHash,
        String status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt) {}
