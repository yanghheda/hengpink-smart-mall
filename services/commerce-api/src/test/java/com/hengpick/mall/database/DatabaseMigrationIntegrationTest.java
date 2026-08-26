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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("integration")
class DatabaseMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("hengpick_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--transaction-isolation=READ-COMMITTED");

    private static Flyway flyway;

    @BeforeAll
    static void migrateEmptyDatabase() {
        flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
    }

    @Test
    void migrationIsIdempotentAndAppliesTheDatabaseConventions() throws SQLException {
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (var connection = mysql.createConnection("");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT @@transaction_isolation")) {
            assertTrue(result.next());
            assertEquals("READ-COMMITTED", result.getString(1));
        }

        try (var connection = mysql.createConnection("");
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN ('users', 'auth_sessions', 'categories', 'products', 'skus', 'shops', 'offers', 'reviews')
                        """)) {
            assertTrue(result.next());
            assertEquals(8, result.getInt(1));
        }
    }

    @Test
    void duplicateBusinessKeysAndInvalidForeignKeysAreRejected() throws SQLException {
        try (var connection = mysql.createConnection("")) {
            insertUser(connection, "01J5D0M8RZ0000000000000001", "demo-user");

            assertThrows(SQLException.class, () -> insertUser(connection, "01J5D0M8RZ0000000000000002", "demo-user"));
            assertThrows(SQLException.class, () -> insertAuthSession(connection, "01J5D0M8RZ0000000000000003", "01J5D0M8RZ0000000000000999"));
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
}
