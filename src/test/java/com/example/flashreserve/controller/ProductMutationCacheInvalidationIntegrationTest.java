
package com.example.flashreserve.controller;

import com.example.flashreserve.service.JwtTokenService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductMutationCacheInvalidationIntegrationTest {

    private static final String PREFIX = "ProductMutationIT-";
    private static final String ADMIN_EMAIL = "product-admin-test@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String productName;
    private String adminToken;
    private Long productId;

    @BeforeEach
    void setUp() {
        clearProductSearchCache();

        productName = PREFIX + UUID.randomUUID();

        var authentication = new UsernamePasswordAuthenticationToken(
                ADMIN_EMAIL,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        adminToken = jwtTokenService.generateToken(authentication);
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

        // Create the product using an ADMIN JWT.
        String response = mockMvc.perform(
                        post("/products")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "%s",
                                          "price": 25.00,
                                          "initialQuantity": 10
                                        }
                                        """.formatted(productName))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(productName))
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(response);
        productId = responseJson.get("id").asLong();

        // The previously cached empty search must no longer be returned.
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
