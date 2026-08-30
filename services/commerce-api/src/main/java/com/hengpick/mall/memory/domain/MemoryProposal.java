package com.hengpick.mall.memory.domain;

import java.time.Instant;
import java.util.Map;

public record MemoryProposal(
        String id,
        String userId,
        String sessionId,
        String proposalType,
        String preferenceKey,
        MemoryScope scope,
        String recipientKey,
        String categoryId,
        Map<String, Object> value,
        String rationaleSummary,
        MemoryProposalStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant decidedAt) {}
