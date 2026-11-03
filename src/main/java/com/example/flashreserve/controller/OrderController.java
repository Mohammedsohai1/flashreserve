
package com.example.flashreserve.controller;

import com.example.flashreserve.dto.AdminOrderResponse;
import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.dto.OrderResponse;
import com.example.flashreserve.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (request.customerEmail() == null
                || !request.customerEmail().strip().equalsIgnoreCase(jwt.getSubject())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only create orders for your own account");
        }

        OrderResponse response =
                orderService.createOrder(request, idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Jwt jwt) {

        OrderResponse response = orderService.cancelOrder(
                orderId,
                jwt.getSubject(),
                isAdmin(jwt));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(orderService.getOrderById(
                orderId,
                jwt.getSubject(),
                isAdmin(jwt)));
    }
    @GetMapping
    public ResponseEntity<Page<AdminOrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }
    private boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ROLE_ADMIN");
    }
}
