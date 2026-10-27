package com.example.flashreserve.service;

import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class

IdempotencyConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM idempotency_keys");
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
                "Idempotency Test Product",
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
    void concurrentRequestsWithSameIdempotencyKeyShouldCreateOnlyOneOrder()
            throws Exception {

        int numberOfRequests = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<OrderResponse>> results = new ArrayList<>();

        CreateOrderRequest request =
                new CreateOrderRequest(productId, 2,"test@example.com");

        try {
            for (int i = 0; i < numberOfRequests; i++) {
                Future<OrderResponse> result = executor.submit(() -> {
                    startLatch.await();

                    return orderService.createOrder(
                            request,
                            "CONCURRENT-KEY-001"
                    );
                });

                results.add(result);
            }

            startLatch.countDown();

            List<OrderResponse> responses = new ArrayList<>();

            // Every request must succeed.
            for (Future<OrderResponse> result : results) {
                responses.add(result.get());
            }

            assertEquals(numberOfRequests, responses.size());

            // All requests must return the same order.
            Long firstOrderId = responses.get(0).id();

            for (OrderResponse response : responses) {
                assertEquals(firstOrderId, response.id());
            }
        } finally {
            // Always stop worker threads, even if an assertion fails.
            executor.shutdownNow();
        }

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders",
                Integer.class
        );

        Integer inventoryQuantity = jdbcTemplate.queryForObject(
                """
                SELECT available_quantity
                FROM inventory
                WHERE product_id = ?
                """,
                Integer.class,
                productId
        );

        System.out.println("Orders created = " + orderCount);
        System.out.println("Inventory = " + inventoryQuantity);

        assertEquals(1, orderCount);
        assertEquals(8, inventoryQuantity);
    }
}