package com.hengpick.mall.identity.domain;

public interface DeletionAuditRepository {
    void record(DeletionAuditRecord record);
}
