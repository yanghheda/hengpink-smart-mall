package com.hengpick.mall.memory.infrastructure;

import java.time.Instant;

record MemoryProposalRow(
        String id,
        String userId,
        String sessionId,
        String proposalType,
        String preferenceKey,
        String scope,
        String recipientKey,
        String categoryId,
        String valueJson,
        String rationaleSummary,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant decidedAt) {}
