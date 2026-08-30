package com.hengpick.mall.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "VM_DATABASE_INTEGRATION", matches = "true")
class DatabaseMigrationIntegrationTest {
    private static Flyway flyway;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void migrateEmptyDatabase() {
        jdbcUrl = requiredEnvironment("MYSQL_URL");
        username = requiredEnvironment("MYSQL_USERNAME");
        password = requiredEnvironment("MYSQL_PASSWORD");
        flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        assertEquals(9, flyway.migrate().migrationsExecuted);
    }

    @Test
    void migrationIsIdempotentAndAppliesTheDatabaseConventions() throws SQLException {
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (var connection = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT @@transaction_isolation")) {
            assertTrue(result.next());
            assertEquals("READ-COMMITTED", result.getString(1));
        }

        try (var connection = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN ('users', 'auth_sessions', 'categories', 'products', 'skus', 'shops', 'offers', 'reviews', 'knowledge_documents', 'deletion_audit_logs', 'decision_sessions', 'decision_messages', 'decision_runs', 'agent_steps', 'decision_run_results', 'memory_proposals', 'user_preferences')
                        """)) {
            assertTrue(result.next());
            assertEquals(17, result.getInt(1));
        }
    }

    @Test
    void decisionRunTableRejectsTwoActiveRunsForOneSession() throws SQLException {
        try (var connection = java.sql.DriverManager.getConnection(jdbcUrl, username, password)) {
            insertUser(connection, "01J5D0M8RZ0000000000000010", "decision-user");
            insertDecisionSession(connection, "01J5D0M8RZ0000000000000011", "01J5D0M8RZ0000000000000010");
            insertDecisionRun(connection, "01J5D0M8RZ0000000000000012", "01J5D0M8RZ0000000000000011", 1,
                    "RUNNING");

            assertThrows(SQLException.class,
                    () -> insertDecisionRun(connection, "01J5D0M8RZ0000000000000013",
                            "01J5D0M8RZ0000000000000011", 2, "RUNNING"));
            insertDecisionRun(connection, "01J5D0M8RZ0000000000000014", "01J5D0M8RZ0000000000000011", 2,
                    "FAILED");
        }
    }

    @Test
    void duplicateBusinessKeysAndInvalidForeignKeysAreRejected() throws SQLException {
        try (var connection = java.sql.DriverManager.getConnection(jdbcUrl, username, password)) {
            insertUser(connection, "01J5D0M8RZ0000000000000001", "demo-user");

            assertThrows(SQLException.class, () -> insertUser(connection, "01J5D0M8RZ0000000000000002", "demo-user"));
            assertThrows(SQLException.class, () -> insertAuthSession(connection, "01J5D0M8RZ0000000000000003", "01J5D0M8RZ0000000000000999"));
        }
    }

    private static String requiredEnvironment(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 VM 数据库集成测试环境变量：" + name);
        }
        return value;
    }

    @Test
    void offerTableContainsTheDeterministicPricingFields() throws SQLException {
        try (var connection = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'offers'
                          AND column_name IN ('list_price', 'sale_price', 'additional_fee', 'valid_from', 'valid_to', 'version')
                        """)) {
            assertTrue(result.next());
            assertEquals(6, result.getInt(1));
        }
    }

    private static void insertUser(Connection connection, String id, String account) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, account, display_name, password_hash, role, status, created_at, updated_at, version)
                    VALUES ('%s', '%s', 'Demo User', '$2a$10$placeholder', 'DEMO_USER', 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0)
                    """.formatted(id, account));
        }
    }

    private static void insertAuthSession(Connection connection, String id, String userId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO auth_sessions (id, user_id, device_session_id, refresh_token_hash, status, expires_at, created_at)
                    VALUES ('%s', '%s', 'device-1', 'hash-%s', 'ACTIVE', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), UTC_TIMESTAMP(3))
                    """.formatted(id, userId, id));
        }
    }

    private static void insertDecisionSession(Connection connection, String id, String userId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO decision_sessions (
                        id, user_id, status, title, intent_json, weights_json, current_run_version,
                        dataset_version, category_schema_version, created_at, updated_at, version
                    ) VALUES (
                        '%s', '%s', 'RUNNING', '测试会话', JSON_OBJECT(), JSON_OBJECT(), 1,
                        'dataset-test', 'phone-test', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0
                    )
                    """.formatted(id, userId));
        }
    }

    private static void insertDecisionRun(
            Connection connection, String id, String sessionId, int runVersion, String status) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO decision_runs (
                        id, session_id, run_version, status, trigger_type, started_at, created_at
                    ) VALUES ('%s', '%s', %d, '%s', 'USER_RETRY', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                    """.formatted(id, sessionId, runVersion, status));
        }
    }
}
