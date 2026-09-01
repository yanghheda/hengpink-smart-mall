package com.hengpick.mall.decision.infrastructure;

public record ReportPublicationContextRow(
        String sessionId,
        String userId,
        String datasetVersion) {}
