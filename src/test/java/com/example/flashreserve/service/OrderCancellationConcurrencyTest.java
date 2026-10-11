package com.example.flashreserve.service;

import com.example.flashreserve.dto.OrderResponse;
import com.example.flashreserve.exception.OrderAlreadyCancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderCancellationConcurrencyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    private Long orderId;
    private Long productId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM idempotency_keys");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM idempotency_key_locks");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM products");

        jdbcTemplate.update(
                "INSERT INTO products (name, price) VALUES (?, ?)",
                "Cancellation Test Product",
                new BigDecimal("10.00")
        );

        productId = jdbcTemplate.queryForObject(
                "SELECT id FROM products ORDER BY id DESC LIMIT 1",
                Long.class
        );

        jdbcTemplate.update(
                "INSERT INTO inventory (product_id, available_quantity) VALUES (?, ?)",
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
    void concurrentCancellationRestoresInventoryOnlyOnce()
            throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to start"
                        );
                    }

                    try {
                        orderService.cancelOrder(orderId);
                        return "SUCCESS";
                    } catch (OrderAlreadyCancelledException ex) {
                        return "CONFLICT";
                    }
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }

            assertEquals(
                    1,
                    outcomes.stream()
                            .filter("SUCCESS"::equals)
                            .count()
            );

            assertEquals(
                    1,
                    outcomes.stream()
                            .filter("CONFLICT"::equals)
                            .count()
            );

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

            assertEquals(10, quantity);
            assertEquals("CANCELLED", status);

        } finally {
            executor.shutdownNow();
        }
    }

}