package com.example.flashreserve.controller;

import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.dto.OrderResponse;
import com.example.flashreserve.entity.Order;
import com.example.flashreserve.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String IdempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResponse response = orderService.createOrder(request,IdempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId) {

        OrderResponse response = orderService.cancelOrder(orderId);

        return ResponseEntity.ok(response);
    }
}

