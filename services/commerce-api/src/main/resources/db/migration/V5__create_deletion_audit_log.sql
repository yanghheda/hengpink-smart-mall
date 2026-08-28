CREATE TABLE deletion_audit_logs (
    id CHAR(26) NOT NULL,
    action VARCHAR(32) NOT NULL,
    subject_hash CHAR(64) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id_hash CHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_deletion_audit_subject_occurred (subject_hash, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
