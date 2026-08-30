package com.hengpick.mall.decision.report;

import java.util.Objects;

/** 报告缓存必须绑定的数据、评分、价格和提示词版本快照。 */
public record ReportVersionSnapshot(
        String datasetVersion,
        String scoringVersion,
        String pricingVersion,
        String promptVersion) {
    public ReportVersionSnapshot {
        requireText(datasetVersion, "数据集版本");
        requireText(scoringVersion, "评分版本");
        requireText(pricingVersion, "价格版本");
        requireText(promptVersion, "提示词版本");
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}
