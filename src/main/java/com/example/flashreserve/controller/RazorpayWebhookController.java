package com.example.flashreserve.controller;


import com.example.flashreserve.service.RazorpayWebhookService;
import com.razorpay.Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.razorpay.RazorpayException;


@RestController
@RequestMapping("/payments")
public class RazorpayWebhookController {


    private final String webhookSecret;
    private final RazorpayWebhookService razorpayWebhookService;


    public RazorpayWebhookController(
            @Value("${razorpay.webhook-secret}") String webhookSecret, RazorpayWebhookService razorpayWebhookService) {
        this.webhookSecret = webhookSecret;
        this.razorpayWebhookService = razorpayWebhookService;
    }


    @PostMapping("/webhook")
    public ResponseEntity<String> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader(
                    value = "X-Razorpay-Signature",
                    required = false) String signature,@RequestHeader(
                    value = "X-Razorpay-Event-Id",
                    required = false
            ) String eventId) {


        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Missing webhook signature");
        }


        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook secret is not configured");
        }


        try {
            boolean valid = Utils.verifyWebhookSignature(
                    rawBody, signature, webhookSecret);


            if (!valid) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid webhook signature");
            }


            // Event processing will be added in the next step.
//            return ResponseEntity.ok("Webhook signature verified");
            razorpayWebhookService.process(rawBody, eventId);
            return ResponseEntity.ok("Webhook processed");

        } catch (RazorpayException ex) {
            return ResponseEntity.badRequest()
                    .body("Webhook signature verification failed");
        }
    }
}
