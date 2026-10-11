CREATE TABLE idempotency_keys (
 id BIGSERIAL PRIMARY KEY,
 idempotency_key VARCHAR(255) NOT NULL,
 order_id BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

 CONSTRAINT uk_idempotency_key
    UNIQUE (idempotency_key),

 CONSTRAINT fk_idempotency_order
    FOREIGN KEY (order_id)
    REFERENCES orders(id)
);