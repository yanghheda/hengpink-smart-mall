package com.hengpick.mall.identity.application;

import com.hengpick.mall.identity.domain.DeletionAuditRecord;
import com.hengpick.mall.identity.domain.DeletionAuditRepository;
import com.hengpick.mall.identity.domain.OwnedObject;
import com.hengpick.mall.identity.domain.RequestSubject;
import com.hengpick.mall.identity.domain.TokenDigester;
import java.time.Clock;

public class ObjectAccessGuard {
    private final DeletionAuditRepository auditRepository;
    private final TokenDigester digester;
    private final Clock clock;

    public ObjectAccessGuard(DeletionAuditRepository auditRepository, TokenDigester digester, Clock clock) {
        this.auditRepository = auditRepository;
        this.digester = digester;
        this.clock = clock;
    }

    public void requireOwner(RequestSubject subject, OwnedObject object) {
        if (!subject.userId().equals(object.ownerId())) {
            throw new ObjectAccessDeniedException("OBJECT_ACCESS_DENIED", "无权访问该资源");
        }
    }

    public void requireTraceAccess(RequestSubject subject, OwnedObject object) {
        requireOwner(subject, object);
        if (!"DEMO_ADMIN".equals(subject.role())) {
            throw new ObjectAccessDeniedException("TRACE_ACCESS_DENIED", "无权访问该资源");
        }
    }

    public void deleteOwnedObject(RequestSubject subject, OwnedObject object, Runnable deletion) {
        requireOwner(subject, object);
        deletion.run();
        auditRepository.record(new DeletionAuditRecord("DELETE", digester.digest(subject.userId()), object.type(),
                digester.digest(object.id()), clock.instant()));
    }
}
