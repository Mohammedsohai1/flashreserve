package com.example.flashreserve.controller;

import com.example.flashreserve.service.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderByIdIntegrationTest {

    private static final String TEST_CUSTOMER_EMAIL =
            "integration-test-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private Long orderId;
    private String accessToken;

    @BeforeEach
    void setUp() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        TEST_CUSTOMER_EMAIL,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                );

        accessToken = jwtTokenService.generateToken(authentication);

        orderId = jdbcTemplate.queryForObject(
                """
                INSERT INTO orders (
                    status,
                    total_amount,
                    created_at,
                    customer_email
                )
                VALUES (?, ?, CURRENT_TIMESTAMP, ?)
                RETURNING id
                """,
                Long.class,
                "CREATED",
                new BigDecimal("99.98"),
                TEST_CUSTOMER_EMAIL
        );

        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    quantity,
                    unit_price
                )
                VALUES (?, ?, ?, ?)
                """,
                orderId,
                123L,
                2,
                new BigDecimal("49.99")
        );
    }

    @AfterEach
    void tearDown() {

        if (orderId != null) {
            jdbcTemplate.update(
                    "DELETE FROM payments WHERE order_id = ?",
                    orderId
            );

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
    }

    @Test
    void shouldReturnOrderWithItsItems() throws Exception {

        mockMvc.perform(
                        get("/orders/{orderId}", orderId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(99.98))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(123))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(49.99));
    }

    @Test
    void shouldReturnNotFoundWhenOrderDoesNotExist() throws Exception {

        mockMvc.perform(
                        get("/orders/{orderId}", -1)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
