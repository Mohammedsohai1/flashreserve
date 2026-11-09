
package com.example.flashreserve.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter your JWT access token"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI flashReserveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlashReserve API")
                        .version("1.0.0")
                        .description(
                                "High-concurrency inventory reservation " +
                                        "and order management platform."
                        )
                        .contact(new Contact()
                                .name("FlashReserve")));
    }
}
