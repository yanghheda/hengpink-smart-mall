package com.hengpick.mall.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.hengpick.mall.decision.application.DecisionRunService;
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
class DecisionSubmissionMapperIntegrationTest {
    @Autowired
    private DecisionRunService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsSessionMessageAndInitialRunAtomically() {
        var started = service.startInitialRun("01JDEMOUSER000000000000001", "给父母买手机，重视续航",
                "commerce-demo-2026.08.1", "phone-v1");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decision_sessions WHERE id = ?", Integer.class,
                started.session().id())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decision_messages WHERE session_id = ?", Integer.class,
                started.session().id())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM decision_runs WHERE id = ? AND status = 'RUNNING'",
                Integer.class, started.run().id())).isOne();
    }
}
