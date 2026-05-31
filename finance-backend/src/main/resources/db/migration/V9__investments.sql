CREATE TABLE investments (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source               VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    platform             VARCHAR(100)   NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    invested_at          DATE           NOT NULL,
    transaction_id       BIGINT REFERENCES transactions(id) ON DELETE SET NULL,
    recipient            VARCHAR(255),
    contact_info         TEXT,
    expected_return_date DATE,
    interest_rate        NUMERIC(5, 2),
    notes                TEXT,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);
