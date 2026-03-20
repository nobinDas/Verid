CREATE TABLE merchant_categories (
    id           BIGSERIAL    PRIMARY KEY,
    merchant_key VARCHAR(255) NOT NULL UNIQUE,
    category     VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_merchant_categories_key ON merchant_categories(merchant_key);
