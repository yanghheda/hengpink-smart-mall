CREATE TABLE decision_sessions (
    id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    intent_json JSON NOT NULL,
    weights_json JSON NOT NULL,
    current_run_version INT UNSIGNED NOT NULL DEFAULT 0,
    current_report_version INT UNSIGNED NULL,
    dataset_version VARCHAR(64) NOT NULL,
    category_schema_version VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_decision_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_decision_sessions_user_created (user_id, created_at DESC),
    INDEX idx_decision_sessions_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE decision_messages (
    id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    run_version INT UNSIGNED NOT NULL,
    role VARCHAR(32) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    content TEXT NULL,
    structured_payload_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_decision_messages_session FOREIGN KEY (session_id) REFERENCES decision_sessions (id),
    INDEX idx_decision_messages_session_version (session_id, run_version, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE decision_runs (
    id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    run_version INT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    active_node VARCHAR(64) NULL,
    failure_code VARCHAR(64) NULL,
    degradation_codes_json JSON NULL,
    model_config_id VARCHAR(64) NULL,
    prompt_version VARCHAR(64) NULL,
    scoring_version VARCHAR(64) NULL,
    pricing_rule_version VARCHAR(64) NULL,
    embedding_version VARCHAR(64) NULL,
    token_input INT UNSIGNED NULL,
    token_output INT UNSIGNED NULL,
    estimated_cost DECIMAL(12,4) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    active_session_id CHAR(26) GENERATED ALWAYS AS (
        CASE WHEN status = 'RUNNING' THEN session_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_decision_runs_session FOREIGN KEY (session_id) REFERENCES decision_sessions (id),
    CONSTRAINT uk_decision_runs_session_version UNIQUE (session_id, run_version),
    CONSTRAINT uk_decision_runs_one_active UNIQUE (active_session_id),
    INDEX idx_decision_runs_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
