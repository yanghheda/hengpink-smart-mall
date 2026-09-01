package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.identity.application.ObjectAccessDeniedException;
import com.hengpick.mall.identity.application.ObjectAccessGuard;
import com.hengpick.mall.identity.domain.DeletionAuditRecord;
import com.hengpick.mall.identity.domain.DeletionAuditRepository;
import com.hengpick.mall.identity.domain.OwnedObject;
import com.hengpick.mall.identity.domain.RequestSubject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectAccessGuardTest {
    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");
    private final CapturingAuditRepository auditRepository = new CapturingAuditRepository();
    private final ObjectAccessGuard guard = new ObjectAccessGuard(auditRepository, value -> "hash:" + value,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final OwnedObject aliceSession = new OwnedObject("DECISION_SESSION", "SESSION-ALICE", "USER-ALICE");

    @Test
    void doesNotRevealWhetherAnotherUsersObjectExists() {
        assertThatThrownBy(() -> guard.requireOwner(new RequestSubject("USER-BOB", "DEMO_USER"), aliceSession))
                .isInstanceOf(ObjectAccessDeniedException.class)
                .extracting("code", "message")
                .containsExactly("OBJECT_ACCESS_DENIED", "无权访问该资源");
    }

    @Test
    void demoAdminCannotBypassOwnerScope() {
        assertThatThrownBy(() -> guard.requireOwner(new RequestSubject("ADMIN-1", "DEMO_ADMIN"), aliceSession))
                .isInstanceOf(ObjectAccessDeniedException.class);
    }

    @Test
    void traceRequiresAdminRoleAndAllowsReadOnlyCrossUserInspection() {
        assertThatThrownBy(() -> guard.requireTraceAccess(new RequestSubject("USER-ALICE", "DEMO_USER"), aliceSession))
                .isInstanceOf(ObjectAccessDeniedException.class)
                .extracting("code").isEqualTo("TRACE_ACCESS_DENIED");

        guard.requireTraceAccess(new RequestSubject("ADMIN-1", "DEMO_ADMIN"), aliceSession);
    }

    @Test
    void recordsOnlyHashedDeletionMetadataAfterOwnerGuardPasses() {
        var deleted = new ArrayList<String>();

        guard.deleteOwnedObject(new RequestSubject("USER-ALICE", "DEMO_USER"), aliceSession,
                () -> deleted.add(aliceSession.id()));

        assertThat(deleted).containsExactly("SESSION-ALICE");
        assertThat(auditRepository.records).containsExactly(new DeletionAuditRecord("DELETE", "hash:USER-ALICE",
                "DECISION_SESSION", "hash:SESSION-ALICE", NOW));
    }

    @Test
    void doesNotDeleteOrAuditWhenOwnerGuardFails() {
        var deleted = new ArrayList<String>();

        assertThatThrownBy(() -> guard.deleteOwnedObject(new RequestSubject("USER-BOB", "DEMO_USER"), aliceSession,
                () -> deleted.add(aliceSession.id()))).isInstanceOf(ObjectAccessDeniedException.class);

        assertThat(deleted).isEmpty();
        assertThat(auditRepository.records).isEmpty();
    }

    private static final class CapturingAuditRepository implements DeletionAuditRepository {
        private final List<DeletionAuditRecord> records = new ArrayList<>();

        @Override
        public void record(DeletionAuditRecord record) {
            records.add(record);
        }
    }
}
