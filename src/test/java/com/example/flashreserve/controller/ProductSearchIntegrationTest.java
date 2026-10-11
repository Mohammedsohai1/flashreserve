package com.example.flashreserve.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;


import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchIntegrationTest {

    private static final String PREFIX = "SearchIT-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();

        insertProduct("SearchIT-Mouse-Cheap", "20.00", 5);
        insertProduct("SearchIT-Mouse-Mid", "30.00", 3);
        insertProduct("SearchIT-Mouse-Expensive", "40.00", 0);
        insertProduct("SearchIT-Keyboard", "25.00", 10);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void shouldSearchFilterByPriceAndStockAndSortResults()
            throws Exception {

        mockMvc.perform(get("/products")
                        .param("search", "mouse")
                        .param("minPrice", "20")
                        .param("maxPrice", "35")
                        .param("inStock", "true")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "price")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchIT-Mouse-Cheap"))
                .andExpect(jsonPath("$.content[0].availableQuantity")
                        .value(5))
                .andExpect(jsonPath("$.content[1].name")
                        .value("SearchIT-Mouse-Mid"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldApplyPaginationAndSorting()
            throws Exception {

        mockMvc.perform(get("/products")
                        .param("search", "SearchIT-")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "price")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name")
                        .value("SearchIT-Mouse-Cheap"))
                .andExpect(jsonPath("$.content[1].name")
                        .value("SearchIT-Keyboard"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void shouldReturnBadRequestForInvalidPriceRange()
            throws Exception {

        mockMvc.perform(get("/products")
                        .param("minPrice", "100")
                        .param("maxPrice", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("minPrice must be less than or equal to maxPrice"));
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

}