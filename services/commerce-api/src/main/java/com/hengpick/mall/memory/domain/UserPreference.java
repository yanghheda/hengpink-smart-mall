package com.hengpick.mall.memory.domain;

import java.time.Instant;
import java.util.Map;

public record UserPreference(
        String id,
        String userId,
        MemoryScope scope,
        String recipientKey,
        String categoryId,
        String preferenceType,
        String preferenceKey,
        Map<String, Object> value,
        String sourceSessionId,
        Instant confirmedAt,
        Instant expiresAt) {}
