package com.hengpick.mall.identity.domain;

import java.time.Instant;

public record DeletionAuditRecord(String action, String subjectHash, String objectType, String objectIdHash,
        Instant occurredAt) {}
