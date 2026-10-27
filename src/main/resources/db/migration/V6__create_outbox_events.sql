CREATE TABLE outbox_events (
  id BIGSERIAL PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,
  aggregate_type VARCHAR(100) NOT NULL,
  aggregate_id VARCHAR(100) NOT NULL,
  event_type VARCHAR(150) NOT NULL,
  payload TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT NULL
);


CREATE INDEX idx_outbox_events_pending
 ON outbox_events (created_at, id)
 WHERE published_at IS NULL;