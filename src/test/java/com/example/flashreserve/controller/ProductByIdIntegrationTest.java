package com.example.flashreserve.controller;

import com.example.flashreserve.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductByIdIntegrationTest {

    private static final String PREFIX = "ProductByIdIT-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;
    @MockitoSpyBean
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        cleanUp();

        productId = jdbcTemplate.queryForObject(
                """
                INSERT INTO products (name, price, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                PREFIX + "Mouse",
                new BigDecimal("49.99")
        );

        jdbcTemplate.update(
                """
                INSERT INTO inventory (product_id, available_quantity)
                VALUES (?, ?)
                """,
                productId,
                7
        );
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void shouldReturnProductById() throws Exception {
        mockMvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value(PREFIX + "Mouse"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.availableQuantity").value(7));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist()
            throws Exception {
        mockMvc.perform(get("/products/{productId}", -1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void shouldReadProductDetailsFromCacheOnSecondRequest()
            throws Exception {

        var cache = cacheManager.getCache("productDetails");
        if (cache == null) {
            throw new IllegalStateException("productDetails cache is missing");
        }

        cache.evict(productId);
        clearInvocations(productRepository);

        mockMvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(PREFIX + "Mouse"));

        mockMvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(PREFIX + "Mouse"));

        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    void shouldReturnFreshInventoryQuantityEvenWhenProductDetailsAreCached()
            throws Exception {

        mockMvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(7));

        // Simulate a successful inventory reservation in the database.
        jdbcTemplate.update(
                """
                UPDATE inventory
                SET available_quantity = available_quantity - 2
                WHERE product_id = ?
                """,
                productId
        );

        mockMvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(5));
    }
    private void cleanUp() {
        jdbcTemplate.update(
                """
                DELETE FROM inventory
                WHERE product_id IN (
                    SELECT id FROM products WHERE name LIKE ?
                )
                """,
                PREFIX + "%"
        );

        jdbcTemplate.update(
                "DELETE FROM products WHERE name LIKE ?",
                PREFIX + "%"
        );
    }
}
