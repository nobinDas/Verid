-- Users
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    currency      VARCHAR(10)  NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Goals
CREATE TYPE term_type AS ENUM ('SHORT', 'MID', 'LONG');
CREATE TYPE goal_status AS ENUM ('ACTIVE', 'COMPLETED', 'CANCELLED');

CREATE TABLE goals (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title          VARCHAR(255)   NOT NULL,
    description    TEXT,
    target_amount  NUMERIC(15, 2) NOT NULL,
    current_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    term_type      term_type      NOT NULL,
    deadline       DATE,
    status         goal_status    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Bank Statements
CREATE TABLE bank_statements (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename    VARCHAR(255) NOT NULL,
    upload_date TIMESTAMP    NOT NULL DEFAULT NOW(),
    month       SMALLINT     NOT NULL,
    year        SMALLINT     NOT NULL,
    processed   BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Transactions
CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT');

CREATE TABLE transactions (
    id           BIGSERIAL        PRIMARY KEY,
    statement_id BIGINT           REFERENCES bank_statements(id) ON DELETE SET NULL,
    user_id      BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date         DATE             NOT NULL,
    description  TEXT             NOT NULL,
    amount       NUMERIC(15, 2)   NOT NULL,
    type         transaction_type NOT NULL,
    category     VARCHAR(100),
    created_at   TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- Categories
CREATE TABLE categories (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE,
    type  VARCHAR(50),
    icon  VARCHAR(100),
    color VARCHAR(20)
);

-- Monthly Summaries
CREATE TABLE monthly_summaries (
    id             BIGSERIAL      PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month          SMALLINT       NOT NULL,
    year           SMALLINT       NOT NULL,
    total_income   NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_expenses NUMERIC(15, 2) NOT NULL DEFAULT 0,
    net_savings    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    top_category   VARCHAR(100),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, month, year)
);

-- AI Chat History
CREATE TYPE chat_role AS ENUM ('user', 'assistant');

CREATE TABLE chat_history (
    id         BIGSERIAL  PRIMARY KEY,
    user_id    BIGINT     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       chat_role  NOT NULL,
    message    TEXT       NOT NULL,
    created_at TIMESTAMP  NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_transactions_user_id  ON transactions(user_id);
CREATE INDEX idx_transactions_date     ON transactions(date);
CREATE INDEX idx_goals_user_id         ON goals(user_id);
CREATE INDEX idx_chat_history_user_id  ON chat_history(user_id);
CREATE INDEX idx_bank_statements_user  ON bank_statements(user_id);
