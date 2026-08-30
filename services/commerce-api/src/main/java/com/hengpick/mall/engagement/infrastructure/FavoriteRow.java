package com.hengpick.mall.engagement.infrastructure;

import java.time.Instant;

record FavoriteRow(
        String id,
        String userId,
        String entityType,
        String entityId,
        String snapshotJson,
        Instant createdAt) {}
