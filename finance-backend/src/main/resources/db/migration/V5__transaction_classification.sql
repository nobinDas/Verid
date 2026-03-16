-- Add classification column to transactions
ALTER TABLE transactions ADD COLUMN classification VARCHAR(20) NOT NULL DEFAULT 'UNCLASSIFIED';

-- Income source rules — remember user preferences per description pattern
CREATE TABLE income_rules (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pattern     TEXT         NOT NULL,   -- normalized description (letters only, lowercase)
    is_income   BOOLEAN      NOT NULL,   -- true = INCOME, false = EXTRA_IN
    seen_count  INTEGER      NOT NULL DEFAULT 1,
    last_seen   DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, pattern)
);
CREATE INDEX idx_income_rules_user_id ON income_rules(user_id);

-- Manual cash entries
CREATE TABLE cash_entries (
    id          BIGSERIAL      PRIMARY KEY,
    user_id     BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date        DATE           NOT NULL,
    description TEXT           NOT NULL,
    amount      NUMERIC(15,2)  NOT NULL,
    type        VARCHAR(10)    NOT NULL CHECK (type IN ('IN', 'OUT')),
    category    VARCHAR(100),
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cash_entries_user_id ON cash_entries(user_id);
