package com.hengpick.mall.identity.infrastructure;

import java.time.Instant;

public record SessionRow(
        String id,
        String userId,
        String deviceSessionId,
        String refreshTokenHash,
        String status,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt) {}
