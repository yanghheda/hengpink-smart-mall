CREATE TABLE decision_reports (
    session_id CHAR(26) NOT NULL,
    version INT UNSIGNED NOT NULL,
    selected_sku_id VARCHAR(64) NOT NULL,
    report_json JSON NOT NULL,
    versions_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (session_id, version),
    CONSTRAINT fk_decision_reports_session FOREIGN KEY (session_id) REFERENCES decision_sessions (id),
    INDEX idx_decision_reports_session_created (session_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE favorites (
    id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(96) NOT NULL,
    snapshot_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_favorites_business UNIQUE (user_id, entity_type, entity_id),
    INDEX idx_favorites_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
