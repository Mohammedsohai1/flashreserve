
package com.example.flashreserve.controller;

import com.example.flashreserve.dto.CreatePaymentResponse;
import com.example.flashreserve.dto.VerifyPaymentRequest;
import com.example.flashreserve.dto.VerifyPaymentResponse;
import com.example.flashreserve.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
            summary = "Create a payment order",
            description = "Creates a Razorpay payment order for an existing customer order. " +
                    "The authenticated customer can create a payment only for their own order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Payment order created successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid order ID or payment request."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required or the JWT is invalid."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The customer is not permitted to create a payment for this order."
            )
    })
    @PostMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse createPayment(

            @Parameter(
                    description = "ID of the order for which payment should be created.",
                    example = "501",
                    required = true
            )
            @PathVariable Long orderId,

            @AuthenticationPrincipal Jwt jwt) {

        return paymentService.createPayment(
                orderId,
                jwt.getSubject()
        );
    }

    @Operation(
            summary = "Verify a payment",
            description = "Verifies the Razorpay payment details and signature for the authenticated customer's order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment verification processed successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payment details or signature."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required or the JWT is invalid."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The customer is not permitted to verify this payment."
            )
    })
    @PostMapping("/verify")
    public VerifyPaymentResponse verifyPayment(

            @Valid
            @RequestBody
            VerifyPaymentRequest request,

            @AuthenticationPrincipal Jwt jwt) {

        return paymentService.verifyPayment(
                request,
                jwt.getSubject()
        );
    }
}
