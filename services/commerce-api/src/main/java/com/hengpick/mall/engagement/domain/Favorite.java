package com.hengpick.mall.engagement.domain;

import java.time.Instant;
import java.util.Map;

public record Favorite(
        String id,
        String userId,
        FavoriteType entityType,
        String entityId,
        Map<String, Object> snapshot,
        Instant createdAt) {}
