CREATE TABLE orders (
 id BIGSERIAL PRIMARY KEY,
 status VARCHAR(50) NOT NULL,
 total_amount NUMERIC(12, 2) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INTEGER NOT NULL,
 unit_price NUMERIC(12, 2) NOT NULL,

 CONSTRAINT fk_order_items_order
 FOREIGN KEY (order_id)
 REFERENCES orders(id),

 CONSTRAINT chk_order_items_quantity
 CHECK (quantity > 0),

 CONSTRAINT chk_order_items_unit_price
 CHECK (unit_price >= 0)
);
