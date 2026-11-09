
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "An individual product entry within an order.")
public record OrderItemResponse(

        @Schema(
                description = "ID of the ordered product.",
                example = "101"
        )
        Long productId,

        @Schema(
                description = "Number of units of this product in the order.",
                example = "2"
        )
        Integer quantity,

        @Schema(
                description = "Price per unit captured when the order was placed.",
                example = "999.99"
        )
        BigDecimal unitPrice
) {
}
