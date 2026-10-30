package com.example.flashreserve.controller;


import com.example.flashreserve.dto.CreatePaymentResponse;
import com.example.flashreserve.dto.VerifyPaymentRequest;
import com.example.flashreserve.dto.VerifyPaymentResponse;
import com.example.flashreserve.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/payments")
public class PaymentController {


    private final PaymentService paymentService;


    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }


    @PostMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse createPayment(
            @PathVariable Long orderId) {
        return paymentService.createPayment(orderId);
    }
    @PostMapping("/verify")
    public VerifyPaymentResponse verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verifyPayment(request);
    }
}
