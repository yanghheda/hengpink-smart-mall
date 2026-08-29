CREATE TABLE agent_steps (
    id CHAR(26) NOT NULL,
    run_id CHAR(26) NOT NULL,
    run_version INT UNSIGNED NOT NULL,
    sequence_no INT UNSIGNED NOT NULL,
    node_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_summary_json JSON NOT NULL,
    output_summary_json JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    duration_ms BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_steps_run FOREIGN KEY (run_id) REFERENCES decision_runs (id),
    CONSTRAINT uk_agent_steps_run_sequence UNIQUE (run_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE decision_run_results (
    run_id CHAR(26) NOT NULL,
    run_version INT UNSIGNED NOT NULL,
    completion_type VARCHAR(32) NOT NULL,
    result_summary_json JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    is_current TINYINT(1) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (run_id),
    CONSTRAINT fk_decision_run_results_run FOREIGN KEY (run_id) REFERENCES decision_runs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
