package com.example.flashreserve.controller;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderInventoryCacheInvalidationIntegrationTest {

    private static final String PREFIX = "OrderCacheIT-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    private String productName;
    private Long productId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        clearSearchCache();

        productName = PREFIX + UUID.randomUUID();

        productId = jdbcTemplate.queryForObject(
                """
                INSERT INTO products (name, price, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                productName,
                new BigDecimal("25.00")
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

    @AfterEach
    void tearDown() {
        clearSearchCache();

        if (orderId != null) {
            jdbcTemplate.update(
                    "DELETE FROM idempotency_keys WHERE order_id = ?",
                    orderId
            );

            jdbcTemplate.update(
                    "DELETE FROM order_items WHERE order_id = ?",
                    orderId
            );

            jdbcTemplate.update(
                    "DELETE FROM orders WHERE id = ?",
                    orderId
            );
        }

        if (productId != null) {
            jdbcTemplate.update(
                    "DELETE FROM inventory WHERE product_id = ?",
                    productId
            );

            jdbcTemplate.update(
                    "DELETE FROM products WHERE id = ?",
                    productId
            );
        }

        clearSearchCache();
    }

    @Test
    void shouldRefreshSearchResultsAfterOrderCreationAndCancellation()
            throws Exception {

        // Initial stock is 10. This request populates the search cache.
        mockMvc.perform(get("/products")
                        .param("search", productName)
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableQuantity")
                        .value(10));

        String idempotencyKey = UUID.randomUUID().toString();

        // Create an order for 3 units.
        String orderResponse = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 3
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        orderId = objectMapper.readTree(orderResponse)
                .get("id")
                .asLong();

        // Stock should now be 7, not the cached value of 10.
        mockMvc.perform(get("/products")
                        .param("search", productName)
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableQuantity")
                        .value(7));

        // Cancel the order and restore the three units.
        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
                .andExpect(status().isOk());

        // Stock should be back to 10.
        mockMvc.perform(get("/products")
                        .param("search", productName)
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].availableQuantity")
                        .value(10));
    }

    private void clearSearchCache() {
        Cache cache = cacheManager.getCache("productSearch");

        if (cache != null) {
            cache.clear();
        }
    }
}