CREATE TABLE receipts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_name        VARCHAR(255),
    purchase_date     DATE,
    total_amount      NUMERIC(12, 2),
    items_json        TEXT,
    image_data        BYTEA,
    original_filename VARCHAR(255),
    content_type      VARCHAR(100),
    retention_years   INTEGER NOT NULL DEFAULT 1,
    expires_at        DATE NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_receipts_user_id ON receipts(user_id);
CREATE INDEX idx_receipts_expires_at ON receipts(expires_at);
