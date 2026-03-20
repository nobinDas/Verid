CREATE TABLE statement_upload_history (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_hash   VARCHAR(64) NOT NULL,
    filename    VARCHAR(255),
    uploaded_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, file_hash)
);
CREATE INDEX idx_upload_history_user_hash ON statement_upload_history(user_id, file_hash);
