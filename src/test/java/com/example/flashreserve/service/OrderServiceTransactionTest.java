package com.example.flashreserve.service;

import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.entity.Inventory;
import com.example.flashreserve.entity.Product;
import com.example.flashreserve.repository.InventoryRepository;
import com.example.flashreserve.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderServiceTransactionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM products");

        productId = jdbcTemplate.queryForObject(
                """
                INSERT INTO products (name, price, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                "Transaction Test Product",
                new BigDecimal("100.00")
        );

        jdbcTemplate.update(
                """
                INSERT INTO inventory (product_id, available_quantity)
                VALUES (?, ?)
                """,
                productId,
                10
        );
    }

    @Test
    void shouldRollbackInventoryReservationWhenOrderCreationFails() {

        // Before
        assertEquals(10, getInventoryQuantity());

        // TODO: make order creation fail after reservation

        // After rollback
        assertEquals(10, getInventoryQuantity());
    }

    private int getInventoryQuantity() {
        return jdbcTemplate.queryForObject(
                """
                SELECT available_quantity
                FROM inventory
                WHERE product_id = ?
                """,
                Integer.class,
                productId
        );
    }
}
