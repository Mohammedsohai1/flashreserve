
package com.example.flashreserve.controller;

import com.example.flashreserve.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchCacheIntegrationTest {

    private static final String PREFIX = "SearchCacheIT-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        clearSearchCache();
        cleanUp();

        insertProduct("SearchCacheIT-Alpha", "20.00", 5);
        insertProduct("SearchCacheIT-Beta", "30.00", 3);
        insertProduct("SearchCacheIT-Gamma", "40.00", 0);
    }

    @AfterEach
    void tearDown() {
        clearSearchCache();
        cleanUp();
    }

    @Test
    void shouldReuseCachedResultsForIdenticalRequests()
            throws Exception {

        clearInvocations(productRepository);

        mockMvc.perform(get("/products")
                        .param("search", PREFIX)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/products")
                        .param("search", PREFIX)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));

        verify(productRepository, times(1))
                .findAll(
                        any(Specification.class),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldCacheDifferentPagesSeparately()
            throws Exception {

        mockMvc.perform(get("/products")
                        .param("search", PREFIX)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchCacheIT-Alpha"))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/products")
                        .param("search", PREFIX)
                        .param("page", "1")
                        .param("size", "1")
                        .param("sortBy", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchCacheIT-Beta"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldKeepDifferentSearchFiltersSeparate()
            throws Exception {

        mockMvc.perform(get("/products")
                        .param("search", "Alpha")
                        .param("sortBy", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchCacheIT-Alpha"));

        mockMvc.perform(get("/products")
                        .param("search", "Beta")
                        .param("sortBy", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchCacheIT-Beta"));
    }

    @Test
    void shouldReflectInventoryChangesAfterSearchCacheEviction()
            throws Exception {

        // 1. Initially Gamma has zero stock, so it must not appear.
        mockMvc.perform(get("/products")
                        .param("search", "SearchCacheIT-Gamma")
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 2. Increase Gamma's stock and verify that exactly one row changed.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE inventory
                SET available_quantity = 5
                WHERE product_id = (
                    SELECT id FROM products WHERE name = ?
                )
                """,
                "SearchCacheIT-Gamma"
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                1, updatedRows,
                "Expected exactly one inventory row to be updated"
        );

        // 3. Verify the database contains the updated quantity.
        Integer quantity = jdbcTemplate.queryForObject(
                """
                SELECT i.available_quantity
                FROM inventory i
                JOIN products p ON p.id = i.product_id
                WHERE p.name = ?
                """,
                Integer.class,
                "SearchCacheIT-Gamma"
        );

        org.junit.jupiter.api.Assertions.assertEquals(5, quantity);

        // 4. Clear the search cache to force a fresh query.
        Cache cache = cacheManager.getCache("productSearch");

        if (cache == null) {
            throw new IllegalStateException(
                    "productSearch cache is not configured"
            );
        }

        cache.clear();

        // 5. The fresh search should now include Gamma with quantity 5.
        mockMvc.perform(get("/products")
                        .param("search", "SearchCacheIT-Gamma")
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchCacheIT-Gamma"))
                .andExpect(jsonPath("$.content[0].availableQuantity")
                        .value(5));
    }

    private void insertProduct(
            String name,
            String price,
            int quantity) {

        Long productId = jdbcTemplate.queryForObject(
                """
                INSERT INTO products (name, price, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                name,
                new BigDecimal(price)
        );

        jdbcTemplate.update(
                """
                INSERT INTO inventory (product_id, available_quantity)
                VALUES (?, ?)
                """,
                productId,
                quantity
        );
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

    private void clearSearchCache() {
        Cache cache = cacheManager.getCache("productSearch");
        if (cache != null) {
            cache.clear();
        }
    }
}

