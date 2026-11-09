
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload containing Razorpay payment details for signature verification.")
public record VerifyPaymentRequest(

        @Schema(
                description = "Internal payment record ID.",
                example = "701",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        Long paymentId,

        @Schema(
                description = "Razorpay order ID returned when the payment order was created.",
                example = "order_Rzp123456789",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String razorpayOrderId,

        @Schema(
                description = "Razorpay payment ID returned after checkout.",
                example = "pay_Rzp987654321",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String razorpayPaymentId,

        @Schema(
                description = "Razorpay checkout signature used for server-side payment verification.",
                example = "generated_razorpay_signature",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String razorpaySignature
) {
}
