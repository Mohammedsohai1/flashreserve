package com.example.flashreserve.service;

import com.example.flashreserve.dto.AdminOrderResponse;
import com.example.flashreserve.dto.CreateOrderRequest;
import com.example.flashreserve.dto.OrderItemResponse;
import com.example.flashreserve.dto.OrderResponse;
import com.example.flashreserve.entity.*;
import com.example.flashreserve.event.OrderCreatedEvent;
import com.example.flashreserve.event.OrderEventPublisher;
import com.example.flashreserve.exception.IdempotencyKeyReusedException;
import com.example.flashreserve.exception.OrderAlreadyCancelledException;
import com.example.flashreserve.exception.OrderNotFoundException;
import com.example.flashreserve.exception.ProductNotFoundException;
import com.example.flashreserve.repository.IdempotencyRepository;
import com.example.flashreserve.repository.InventoryRepository;
import com.example.flashreserve.repository.OrderRepository;
import com.example.flashreserve.repository.ProductRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CacheManager cacheManager;
    private final IdempotencyRepository idempotencyRepository;
    private final IdempotencyFingerprintService idempotencyFingerprintService;
    private final OrderEventPublisher orderEventPublisher;
    private final OutboxEventService outboxEventService;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            ProductService productService,
            IdempotencyRepository idempotencyRepository,
            IdempotencyFingerprintService idempotencyFingerprintService,
            InventoryRepository inventoryRepository, CacheManager cacheManager, OrderEventPublisher orderEventPublisher, OutboxEventService outboxEventService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.idempotencyRepository=idempotencyRepository;
        this.idempotencyFingerprintService=idempotencyFingerprintService;
        this.inventoryRepository=inventoryRepository;
        this.cacheManager = cacheManager;
        this.orderEventPublisher = orderEventPublisher;
        this.outboxEventService = outboxEventService;
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
                LocalDateTime.now(),
                request.customerEmail()
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
//        OrderCreatedEvent event = new OrderCreatedEvent(
//                java.util.UUID.randomUUID().toString(),
//                savedOrder.getId(),
//                savedOrder.getTotalAmount(),
//                savedOrder.getCreatedAt(),
//                request.customerEmail()
//        );
//
//
//        outboxEventService.recordOrderCreatedEvent(event);
        evictProductSearchCacheAfterCommit();
        return toOrderResponse(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        // Internal use by the expiration scheduler.
        return cancelOrderInternal(orderId, null, false);
    }

    @Transactional
    public OrderResponse cancelOrder(
            Long orderId, String userEmail, boolean isAdmin) {
        return cancelOrderInternal(orderId, userEmail, !isAdmin);
    }

    private OrderResponse cancelOrderInternal(
            Long orderId, String userEmail, boolean enforceOwnership) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(OrderNotFoundException::new);

        if (enforceOwnership) {
            verifyOwnership(order, userEmail);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException();
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException(
                    "Only unpaid CREATED orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);

        for (OrderItem item : order.getItems()) {
            int updatedRows = inventoryRepository.increaseQuantity(
                    item.getProductId(),
                    item.getQuantity());

            if (updatedRows != 1) {
                throw new IllegalStateException(
                        "Could not restore inventory for product "
                                + item.getProductId());
            }
        }

        evictProductSearchCacheAfterCommit();
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
        // Preserve compatibility for existing internal callers and tests.
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(
            Long orderId, String userEmail, boolean isAdmin) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        if (!isAdmin) {
            verifyOwnership(order, userEmail);
        }

        return toOrderResponse(order);
    }

    private void verifyOwnership(Order order, String userEmail) {
        if (userEmail == null
                || order.getCustomerEmail() == null
                || !order.getCustomerEmail().equalsIgnoreCase(userEmail)) {
            // Return 404 rather than disclose another customer's order.
            throw new OrderNotFoundException();
        }
    }
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(order -> new AdminOrderResponse(
                        order.getId(),
                        order.getCustomerEmail(),
                        order.getStatus().name(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        order.getItems().stream()
                                .map(item -> new OrderItemResponse(
                                        item.getProductId(),
                                        item.getQuantity(),
                                        item.getUnitPrice()
                                ))
                                .toList()
                ));
    }

    private void evictProductSearchCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            Cache cache = cacheManager.getCache("productSearch");
                            if (cache != null) {
                                cache.clear();
                            }
                        }
                    }
            );
        } else {
            Cache cache = cacheManager.getCache("productSearch");
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
