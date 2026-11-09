
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Order details returned to the customer.")
public record OrderResponse(

        @Schema(description = "Unique order ID.", example = "501")
        Long id,

        @Schema(description = "Current order status.", example = "PENDING")
        String status,

        @Schema(
                description = "Total order amount.",
                example = "1999.98"
        )
        BigDecimal totalAmount,

        @Schema(description = "Items included in the order.")
        List<OrderItemResponse> items
) {
}
