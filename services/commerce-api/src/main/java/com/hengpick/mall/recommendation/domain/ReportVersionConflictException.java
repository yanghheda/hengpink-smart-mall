package com.hengpick.mall.recommendation.domain;

public final class ReportVersionConflictException extends RuntimeException {

    public ReportVersionConflictException(long expectedVersion, long currentVersion) {
        super("报告版本冲突：期望版本 " + expectedVersion + "，当前版本 " + currentVersion);
    }
}
