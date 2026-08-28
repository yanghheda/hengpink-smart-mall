package com.hengpick.mall.identity.infrastructure;

import java.time.Instant;

public record DeletionAuditRow(String id, String action, String subjectHash, String objectType, String objectIdHash,
        Instant occurredAt) {}
