ALTER TABLE goals
    ADD COLUMN allocation_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;

CREATE TABLE recurring_expenses (
    id           BIGSERIAL      PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    description  TEXT           NOT NULL,
    amount       NUMERIC(15, 2) NOT NULL,
    day_of_month SMALLINT       NOT NULL,
    category     VARCHAR(100),
    confirmed    BOOLEAN        NOT NULL DEFAULT FALSE,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_expenses_user_id ON recurring_expenses(user_id);
