CREATE TABLE goal_deposits (
    id         BIGSERIAL PRIMARY KEY,
    goal_id    BIGINT         NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    user_id    BIGINT         NOT NULL REFERENCES users(id),
    amount     NUMERIC(15, 2) NOT NULL,
    month      SMALLINT       NOT NULL,
    year       SMALLINT       NOT NULL,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goal_deposits_user ON goal_deposits(user_id);
CREATE INDEX idx_goal_deposits_goal  ON goal_deposits(goal_id);
