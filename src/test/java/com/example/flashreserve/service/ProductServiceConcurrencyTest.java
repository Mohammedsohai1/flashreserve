package com.example.flashreserve.service;

import com.example.flashreserve.entity.Inventory;
import com.example.flashreserve.entity.Product;
import com.example.flashreserve.exception.InsufficientInventoryException;
import com.example.flashreserve.repository.InventoryRepository;
import com.example.flashreserve.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ProductServiceConcurrencyTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    private Long productId;
    @BeforeEach
    void setup() {

        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM products");

        Long generatedProductId = jdbcTemplate.queryForObject(
                """
                INSERT INTO products (name, price, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                "Flash Sale Product",
                new BigDecimal("100.00")
        );

        productId = generatedProductId;

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
    void shouldNotOversellInventory() throws InterruptedException,ExecutionException {

        int numberOfRequests = 50;

        ExecutorService executor = Executors.newFixedThreadPool(20);

        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {

            Future<Boolean> result = executor.submit(() -> {

                startLatch.await();

                try {
                    productService.reserve(productId, 1);
                    return true;
                } catch (InsufficientInventoryException e) {
                    return false;
                }
            });

            results.add(result);
        }

        startLatch.countDown();

        int successfulReservations = 0;

        for (Future<Boolean> result : results) {
            if (result.get()) {
                successfulReservations++;
            }
        }

        executor.shutdown();

        Inventory inventory =
                inventoryRepository.findAll().get(0);

        assertEquals(10, successfulReservations);
        assertEquals(0, inventory.getAvailableQuantity());
    }
}
