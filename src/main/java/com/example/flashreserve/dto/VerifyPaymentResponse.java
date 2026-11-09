
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of Razorpay payment verification.")
public record VerifyPaymentResponse(

        @Schema(description = "Internal payment record ID.", example = "701")
        Long paymentId,

        @Schema(description = "Final payment status.", example = "SUCCESS")
        String status,

        @Schema(description = "Message describing the verification result.", example = "Payment verified successfully.")
        String message
) {
}
