package com.example.flashreserve.service;

import com.example.flashreserve.entity.OrderStatus;
import com.example.flashreserve.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderExpirationService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderExpirationService.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final long timeoutMinutes;

    public OrderExpirationService(
            OrderRepository orderRepository,
            OrderService orderService,
            @Value("${app.orders.expiration-minutes:15}")
            long timeoutMinutes) {

        if (timeoutMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Order expiration timeout must be positive");
        }

        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelayString =
            "${app.orders.expiration-poll-interval-ms:30000}")
    public void expireAbandonedOrders() {

        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<Long> orderIds = orderRepository.findExpiredOrderIds(
                OrderStatus.CREATED,
                cutoff);

        for (Long orderId : orderIds) {
            try {
                orderService.cancelOrder(orderId);

                log.info(
                        "Expired unpaid order and released inventory: orderId={}",
                        orderId);

            } catch (IllegalStateException exception) {
                // The order may have been paid or cancelled since discovery.
                log.info(
                        "Skipping order {} because its state changed: {}",
                        orderId,
                        exception.getMessage());

            } catch (Exception exception) {
                log.error(
                        "Failed to expire unpaid order {}. It can be retried on the next poll.",
                        orderId,
                        exception);
            }
        }
    }
}
