package com.hengpick.mall.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.identity.application.AuthService;
import com.hengpick.mall.identity.application.AuthenticationFailedException;
import com.hengpick.mall.identity.application.ObjectAccessGuard;
import com.hengpick.mall.identity.domain.OwnedObject;
import com.hengpick.mall.identity.domain.RequestSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "VM_DATABASE_INTEGRATION", matches = "true")
@ActiveProfiles("database")
@SpringBootTest
class AuthMapperIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectAccessGuard objectAccessGuard;

    @BeforeEach
    void clearSessions() {
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM deletion_audit_logs");
    }

    @Test
    void logsInWithSeedAccountAndPersistsOnlyRotatedRefreshHashes() {
        var login = authService.login("demo_user", "demo123456", "integration-device");
        var storedAfterLogin = jdbc.queryForObject(
                "SELECT refresh_token_hash FROM auth_sessions WHERE device_session_id = 'integration-device'",
                String.class);

        assertThat(storedAfterLogin).hasSize(64).isNotEqualTo(login.refreshToken());

        var refreshed = authService.refresh(login.refreshToken());
        var storedAfterRefresh = jdbc.queryForObject(
                "SELECT refresh_token_hash FROM auth_sessions WHERE device_session_id = 'integration-device'",
                String.class);

        assertThat(storedAfterRefresh).hasSize(64).isNotEqualTo(storedAfterLogin).isNotEqualTo(refreshed.refreshToken());
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void rejectsDisabledSeedAccount() {
        assertThatThrownBy(() -> authService.login("disabled_user", "demo123456", "integration-device"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("账号或密码错误");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions", Integer.class)).isZero();
    }

    @Test
    void persistsOnlyHashedDeletionAuditMetadata() {
        objectAccessGuard.deleteOwnedObject(new RequestSubject("USER-1", "DEMO_USER"),
                new OwnedObject("DECISION_SESSION", "SESSION-1", "USER-1"), () -> {});

        var audit = jdbc.queryForMap("""
                SELECT action, subject_hash, object_type, object_id_hash
                FROM deletion_audit_logs
                """);

        assertThat(audit).containsEntry("action", "DELETE").containsEntry("object_type", "DECISION_SESSION");
        assertThat(audit.get("subject_hash")).isNotEqualTo("USER-1");
        assertThat(audit.get("object_id_hash")).isNotEqualTo("SESSION-1");
    }
}
