package com.example.flashreserve.controller;



import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductMutationCacheInvalidationIntegrationTest {

    private static final String PREFIX = "ProductMutationIT-";

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

    @BeforeEach
    void setUp() {
        clearProductSearchCache();
        productName = PREFIX + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        clearProductSearchCache();

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

        clearProductSearchCache();
    }

    @Test
    void shouldInvalidateProductSearchCacheAfterProductCreation()
            throws Exception {

        // Prime the cache with a search that returns no matching products.
        mockMvc.perform(get("/products")
                        .param("search", productName)
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Create the product through the actual REST endpoint.
        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "price": 25.00,
                                  "initialQuantity": 10
                                }
                                """.formatted(productName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(productName))
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(response);
        productId = responseJson.get("id").asLong();

        // The previous cached empty search must no longer be returned.
        mockMvc.perform(get("/products")
                        .param("search", productName)
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.content[0].availableQuantity").value(10));
    }

    private void clearProductSearchCache() {
        Cache cache = cacheManager.getCache("productSearch");

        if (cache != null) {
            cache.clear();
        }
    }
}