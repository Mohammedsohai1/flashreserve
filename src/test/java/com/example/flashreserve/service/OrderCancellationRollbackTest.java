package com.example.flashreserve.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderCancellationRollbackTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    private Long productId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        // Remove any test trigger left by an interrupted run.
        jdbcTemplate.execute("""
                DROP TRIGGER IF EXISTS test_fail_order_cancellation
                ON orders
                """);
        jdbcTemplate.execute("""
                DROP FUNCTION IF EXISTS test_fail_order_cancellation()
                """);

        jdbcTemplate.update("DELETE FROM idempotency_keys");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM idempotency_key_locks");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM products");

        jdbcTemplate.update(
                "INSERT INTO products (name, price) VALUES (?, ?)",
                "Rollback Test Product",
                new BigDecimal("10.00")
        );

        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM products ORDER BY id DESC LIMIT 1",
                Long.class
        );

        // Two units were reserved when the order was created.
        jdbcTemplate.update(
                """
                INSERT INTO inventory (product_id, available_quantity)
                VALUES (?, ?)
                """,
                productId, 8
        );

        jdbcTemplate.update(
                """
                INSERT INTO orders (status, total_amount)
                VALUES ('CREATED', ?)
                """,
                new BigDecimal("20.00")
        );

        orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders ORDER BY id DESC LIMIT 1",
                Long.class
        );

        jdbcTemplate.update(
                """
                INSERT INTO order_items
                    (order_id, product_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)
                """,
                orderId, productId, 2, new BigDecimal("10.00")
        );
    }

    @Test
    void inventoryRestorationRollsBackWhenStatusUpdateFails() {
        try {
            // Force PostgreSQL to reject the cancellation status update.
            jdbcTemplate.execute("""
                    CREATE FUNCTION test_fail_order_cancellation()
                    RETURNS trigger AS $$
                    BEGIN
                        IF NEW.status = 'CANCELLED' THEN
                            RAISE EXCEPTION
                                'Simulated cancellation status failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);

            jdbcTemplate.execute("""
                    CREATE TRIGGER test_fail_order_cancellation
                    BEFORE UPDATE OF status ON orders
                    FOR EACH ROW
                    EXECUTE FUNCTION test_fail_order_cancellation()
                    """);

            // The service call must fail when the transaction flushes.
            assertThrows(RuntimeException.class, () ->
                    orderService.cancelOrder(orderId)
            );

            // Inventory restoration must have rolled back.
            Integer quantity = jdbcTemplate.queryForObject(
                    """
                    SELECT available_quantity
                    FROM inventory
                    WHERE product_id = ?
                    """,
                    Integer.class,
                    productId
            );

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?",
                    String.class,
                    orderId
            );

            assertEquals(8, quantity);
            assertEquals("CREATED", status);

        } finally {
            // Do not leave test-only database objects behind.
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS test_fail_order_cancellation
                    ON orders
                    """);
            jdbcTemplate.execute("""
                    DROP FUNCTION IF EXISTS test_fail_order_cancellation()
                    """);
        }
    }
}