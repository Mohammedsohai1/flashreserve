
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload for creating a customer order.")
public record CreateOrderRequest(

        @Schema(
                description = "ID of the product to order.",
                example = "101",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        Long productId,

        @Schema(
                description = "Number of units to order. Must be greater than zero.",
                example = "2",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @Positive
        Integer quantity,

        @Schema(
                description = "Email address of the customer placing the order. Must match the authenticated user's email.",
                example = "customer@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Email
        String customerEmail
) {
}
