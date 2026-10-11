
CREATE TABLE products (
 id BIGSERIAL PRIMARY KEY,
 name VARCHAR(255) NOT NULL,
 price NUMERIC(12, 2) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL,
 available_quantity INTEGER NOT NULL,

 CONSTRAINT fk_inventory_product
 FOREIGN KEY (product_id)
 REFERENCES products(id),

 CONSTRAINT uk_inventory_product
 UNIQUE (product_id),

 CONSTRAINT chk_inventory_quantity
 CHECK (available_quantity >= 0)
);