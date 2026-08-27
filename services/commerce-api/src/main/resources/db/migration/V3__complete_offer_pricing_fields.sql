ALTER TABLE offers
    ADD COLUMN list_price DECIMAL(12,2) NULL AFTER shop_id,
    ADD COLUMN additional_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER sale_price,
    ADD COLUMN valid_from DATETIME(3) NULL AFTER stock_status,
    ADD COLUMN valid_to DATETIME(3) NULL AFTER valid_from,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status;

UPDATE offers
SET list_price = sale_price,
    valid_from = '1970-01-01 00:00:00.000',
    valid_to = '9999-12-31 23:59:59.999'
WHERE list_price IS NULL OR valid_from IS NULL OR valid_to IS NULL;

ALTER TABLE offers
    MODIFY COLUMN list_price DECIMAL(12,2) NOT NULL,
    MODIFY COLUMN valid_from DATETIME(3) NOT NULL,
    MODIFY COLUMN valid_to DATETIME(3) NOT NULL,
    DROP INDEX idx_offers_sku_status,
    ADD INDEX idx_offers_sku_status_valid_to (sku_id, status, valid_to);
