ALTER TABLE idempotency_keys
ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL;