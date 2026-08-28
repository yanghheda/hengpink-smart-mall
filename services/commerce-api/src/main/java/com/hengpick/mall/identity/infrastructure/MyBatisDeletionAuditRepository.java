package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.DeletionAuditRecord;
import com.hengpick.mall.identity.domain.DeletionAuditRepository;
import com.hengpick.mall.shared.UlidGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class MyBatisDeletionAuditRepository implements DeletionAuditRepository {
    private final IdentityMapper mapper;
    private final UlidGenerator ulidGenerator;

    public MyBatisDeletionAuditRepository(IdentityMapper mapper, UlidGenerator ulidGenerator) {
        this.mapper = mapper;
        this.ulidGenerator = ulidGenerator;
    }

    @Override
    public void record(DeletionAuditRecord record) {
        mapper.insertDeletionAudit(new DeletionAuditRow(ulidGenerator.next(), record.action(), record.subjectHash(),
                record.objectType(), record.objectIdHash(), record.occurredAt()));
    }
}
