package com.example.flashreserve.service;

import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.dto.OrderItemResponse;
import com.example.flashreserve.dto.OrderResponse;
import com.example.flashreserve.entity.*;
import com.example.flashreserve.exception.IdempotencyKeyReusedException;
import com.example.flashreserve.exception.OrderAlreadyCancelledException;
import com.example.flashreserve.exception.OrderNotFoundException;
import com.example.flashreserve.exception.ProductNotFoundException;
import com.example.flashreserve.repository.IdempotencyRepository;
import com.example.flashreserve.repository.InventoryRepository;
import com.example.flashreserve.repository.OrderRepository;
import com.example.flashreserve.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final IdempotencyRepository idempotencyRepository;
    private final IdempotencyFingerprintService idempotencyFingerprintService;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            ProductService productService,
            IdempotencyRepository idempotencyRepository,
            IdempotencyFingerprintService idempotencyFingerprintService,
             InventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.idempotencyRepository=idempotencyRepository;
        this.idempotencyFingerprintService=idempotencyFingerprintService;
        this.inventoryRepository=inventoryRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request,String idempotencyKey) {
        String requestFingerprint= idempotencyFingerprintService.generate(request);
        idempotencyRepository.insertLockIfAbsent(idempotencyKey);
        idempotencyRepository.lockByIdempotencyKey(idempotencyKey);
        IdempotencyKey existingKey =
                idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existingKey != null) {

            if (!existingKey.getRequestFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyKeyReusedException();
            }

            return toOrderResponse(existingKey.getOrder());
        }
        Product product = productRepository.findById(request.productId())
                .orElseThrow(ProductNotFoundException::new);

        productService.reserve(
                request.productId(),
                request.quantity()
        );
//        throw new RuntimeException("Simulated order creation failure");

        BigDecimal totalAmount = product.getPrice()
                .multiply(BigDecimal.valueOf(request.quantity()));

        Order order = new Order(
                OrderStatus.CREATED,
                totalAmount,
                LocalDateTime.now()
        );

        OrderItem orderItem = new OrderItem(
                product.getId(),
                request.quantity(),
                product.getPrice()
        );


        order.addItem(orderItem);

        Order savedOrder = orderRepository.save(order);
        IdempotencyKey savedKey = new IdempotencyKey(
                idempotencyKey,
                requestFingerprint,
                savedOrder,
                LocalDateTime.now()
        );

        idempotencyRepository.save(savedKey);
        return toOrderResponse(savedOrder);
    }
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(OrderNotFoundException::new);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException();
        }

        for (OrderItem item : order.getItems()) {
            int updatedRows = inventoryRepository.increaseQuantity(
                    item.getProductId(),
                    item.getQuantity()
            );

            if (updatedRows != 1) {
                throw new IllegalStateException(
                        "Could not restore inventory for product "
                                + item.getProductId()
                );
            }
        }

        order.setStatus(OrderStatus.CANCELLED);

        return toOrderResponse(order);
    }

    private OrderResponse toOrderResponse(Order order) {
                List<OrderItemResponse> itemResponses = order.getItems()
                .stream()
               .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
               .toList();

        return new OrderResponse(
               order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
               itemResponses
        );
    }
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        return toOrderResponse(order);
    }
}
