
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment order details returned after creating a Razorpay payment order.")
public record CreatePaymentResponse(

        @Schema(description = "Internal payment record ID.", example = "701")
        Long paymentId,

        @Schema(description = "Internal order ID associated with this payment.", example = "501")
        Long orderId,

        @Schema(description = "Razorpay order ID used by the checkout integration.", example = "order_Rzp123456789")
        String razorpayOrderId,

        @Schema(description = "Payment amount in the smallest currency unit (paise for INR).", example = "199999")
        Long amountPaise,

        @Schema(description = "Currency used for the payment.", example = "INR")
        String currency,

        @Schema(description = "Razorpay public Key ID used by the frontend checkout.", example = "rzp_test_xxxxxxxxxxxxxx")
        String razorpayKeyId
) {
}
