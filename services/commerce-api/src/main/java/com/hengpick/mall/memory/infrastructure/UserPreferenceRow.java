package com.hengpick.mall.memory.infrastructure;

import java.time.Instant;

record UserPreferenceRow(
        String id,
        String userId,
        String scope,
        String recipientKey,
        String categoryId,
        String preferenceType,
        String preferenceKey,
        String valueJson,
        String sourceSessionId,
        Instant confirmedAt,
        Instant expiresAt) {}
