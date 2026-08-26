CREATE TABLE shops (
    id VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_shops_dataset_version (dataset_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE offers (
    id VARCHAR(80) NOT NULL,
    sku_id CHAR(26) NOT NULL,
    shop_id VARCHAR(80) NOT NULL,
    sale_price DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    stock_status VARCHAR(16) NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_offers_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT fk_offers_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    INDEX idx_offers_sku_status (sku_id, status),
    INDEX idx_offers_dataset_version (dataset_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
    id VARCHAR(80) NOT NULL,
    product_id CHAR(26) NOT NULL,
    sku_id CHAR(26) NULL,
    rating TINYINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    is_simulated TINYINT(1) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reviews_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_product (product_id),
    INDEX idx_reviews_sku (sku_id),
    INDEX idx_reviews_dataset_version (dataset_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
