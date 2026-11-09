
package com.example.flashreserve.controller;

import com.example.flashreserve.dto.AdminOrderResponse;
import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.dto.OrderResponse;
import com.example.flashreserve.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@RestController
@RequestMapping("/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(
            summary = "Create an order",
            description = """
                    Creates an order for the authenticated customer.
                    Inventory is reserved during order creation.
                    An Idempotency-Key header is required to prevent
                    duplicate order creation.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing idempotency key"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid token"),
            @ApiResponse(responseCode = "403", description = "Customer cannot create an order for another account"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "Insufficient inventory or idempotency conflict")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(

            @Parameter(
                    description = "Unique key used to prevent duplicate order creation",
                    required = true,
                    example = "ORDER-REQUEST-10001"
            )
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

    @Operation(
            summary = "Cancel an order",
            description = """
                    Cancels an existing order belonging to the authenticated
                    customer. Administrators can also cancel orders.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid token"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled in its current state")
    })
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "ID of the order to cancel", example = "101")
            @PathVariable Long orderId,

            @AuthenticationPrincipal Jwt jwt) {

        OrderResponse response = orderService.cancelOrder(
                orderId,
                jwt.getSubject(),
                isAdmin(jwt));

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get order by ID",
            description = """
                    Retrieves an order by its unique ID.
                    Customers can access their own orders.
                    Administrators can access all orders.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid token"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Unique order ID", example = "101")
            @PathVariable Long orderId,

            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(orderService.getOrderById(
                orderId,
                jwt.getSubject(),
                isAdmin(jwt)));
    }

    @Operation(
            summary = "Get all orders",
            description = """
                    Retrieves a paginated list of orders.
                    Intended for administrator order management.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid token"),
            @ApiResponse(responseCode = "403", description = "Administrator access required")
    })
    @GetMapping
    public ResponseEntity<Page<AdminOrderResponse>> getAllOrders(

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of orders per page (maximum 100)", example = "20")
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
