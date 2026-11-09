
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Detailed order information returned to administrators.")
public record AdminOrderResponse(

        @Schema(description = "Unique order ID.", example = "501")
        Long id,

        @Schema(
                description = "Email address of the customer who placed the order.",
                example = "customer@example.com"
        )
        String customerEmail,

        @Schema(description = "Current order status.", example = "PENDING")
        String status,

        @Schema(
                description = "Total order amount.",
                example = "1999.98"
        )
        BigDecimal totalAmount,

        @Schema(
                description = "Date and time when the order was created.",
                example = "2026-11-09T14:30:00"
        )
        LocalDateTime createdAt,

        @Schema(description = "Items included in the order.")
        List<OrderItemResponse> items
) {
}
