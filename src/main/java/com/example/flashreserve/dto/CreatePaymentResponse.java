package com.example.flashreserve.dto;

public record CreatePaymentResponse(
        Long paymentId,
        Long orderId,
        String razorpayOrderId,
        Long amountPaise,
        String currency,
        String razorpayKeyId
) {}

