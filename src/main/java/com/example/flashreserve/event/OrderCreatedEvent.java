package com.example.flashreserve.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        BigDecimal totalAmount,
        LocalDateTime occurredAt,
        String customerEmail
) {
}
