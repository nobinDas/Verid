ALTER TABLE bank_statements
    ADD COLUMN bank_name     VARCHAR(50),
    ADD COLUMN account_last4 VARCHAR(4),
    ADD COLUMN account_type  VARCHAR(20),
    ADD COLUMN file_hash     VARCHAR(64),
    ADD COLUMN file_data     BYTEA;
