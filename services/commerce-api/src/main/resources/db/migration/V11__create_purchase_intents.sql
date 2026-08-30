CREATE TABLE purchase_intents (
    id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    session_id CHAR(26) NOT NULL,
    report_version INT UNSIGNED NOT NULL,
    sku_id CHAR(26) NOT NULL,
    price_plan_snapshot_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_intents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_purchase_intents_report FOREIGN KEY (session_id, report_version)
        REFERENCES decision_reports (session_id, version),
    CONSTRAINT fk_purchase_intents_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT uk_purchase_intents_idempotency UNIQUE (user_id, idempotency_key),
    INDEX idx_purchase_intents_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
