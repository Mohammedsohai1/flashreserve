package com.example.flashreserve.dto;

public record VerifyPaymentResponse(
        Long paymentId,
        String status,
        String message
) {
}
