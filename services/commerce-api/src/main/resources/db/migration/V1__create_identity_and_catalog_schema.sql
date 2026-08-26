CREATE TABLE users (
    id CHAR(26) NOT NULL,
    account VARCHAR(64) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_account UNIQUE (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_sessions (
    id CHAR(26) NOT NULL,
    user_id CHAR(26) NOT NULL,
    device_session_id VARCHAR(128) NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_auth_sessions_user_status (user_id, status),
    INDEX idx_auth_sessions_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    id CHAR(26) NOT NULL,
    parent_id CHAR(26) NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(80) NOT NULL,
    depth_level TINYINT UNSIGNED NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    schema_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    INDEX idx_categories_parent_status (parent_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id CHAR(26) NOT NULL,
    category_id CHAR(26) NOT NULL,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    canonical_variant VARCHAR(120) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    subtitle VARCHAR(255) NULL,
    canonical_specs_json JSON NOT NULL,
    selling_points_json JSON NOT NULL,
    limitation_json JSON NOT NULL,
    warranty_summary VARCHAR(500) NULL,
    dataset_version VARCHAR(64) NOT NULL,
    is_simulated TINYINT(1) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_canonical_variant UNIQUE (brand, model, canonical_variant),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    INDEX idx_products_category_status (category_id, status),
    INDEX idx_products_dataset_version (dataset_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skus (
    id CHAR(26) NOT NULL,
    product_id CHAR(26) NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    attributes_json JSON NOT NULL,
    stock_status VARCHAR(16) NOT NULL,
    stock_quantity INT NOT NULL,
    warranty_months SMALLINT UNSIGNED NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_skus_sku_code UNIQUE (sku_code),
    CONSTRAINT fk_skus_product FOREIGN KEY (product_id) REFERENCES products (id),
    INDEX idx_skus_product_status (product_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
