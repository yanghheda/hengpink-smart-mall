package com.hengpick.mall.checkout.infrastructure;

import java.time.LocalDateTime;

record PurchaseIntentRow(String id, String userId, String sessionId, int reportVersion, String skuId,
        String snapshotJson, String status, LocalDateTime expiresAt, LocalDateTime createdAt,
        LocalDateTime confirmedAt, String idempotencyKey) {}
