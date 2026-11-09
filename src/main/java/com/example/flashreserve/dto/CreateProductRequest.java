
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Request payload for creating a new product")
public record CreateProductRequest(

        @Schema(
                description = "Product name",
                example = "iPhone 17 Pro",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String name,

        @Schema(
                description = "Product price",
                example = "1299.99",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @Positive
        BigDecimal price,

        @Schema(
                description = "Initial available inventory quantity",
                example = "100",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @PositiveOrZero
        Integer initialQuantity

) {
}
