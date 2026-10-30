CREATE TABLE processed_webhook_events
(
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);


CREATE INDEX idx_processed_webhook_events_type
    ON processed_webhook_events (event_type);