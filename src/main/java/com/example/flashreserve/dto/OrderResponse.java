package com.example.flashreserve.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items
) {
}
