
package com.example.flashreserve.service;

import com.example.flashreserve.entity.Order;
import com.example.flashreserve.event.OrderCreatedEvent;
import com.example.flashreserve.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RazorpayWebhookService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxEventService outboxEventService;
    private final OrderRepository orderRepository;

    public RazorpayWebhookService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            OutboxEventService outboxEventService,
            OrderRepository orderRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.outboxEventService = outboxEventService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void process(String rawBody, String eventId) {

        if (eventId == null || eventId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing Razorpay event ID");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("event").asText();

            if (!eventType.equals("payment.captured")
                    && !eventType.equals("payment.failed")) {
                return;
            }

            JsonNode paymentNode = root.path("payload")
                    .path("payment")
                    .path("entity");

            String razorpayPaymentId =
                    paymentNode.path("id").asText(null);

            String razorpayOrderId =
                    paymentNode.path("order_id").asText(null);

            String currency =
                    paymentNode.path("currency").asText(null);

            long amount = paymentNode.path("amount").asLong(-1);

            if (razorpayPaymentId == null
                    || razorpayOrderId == null
                    || currency == null
                    || amount <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Missing payment details in webhook");
            }

            // Record the webhook event. Duplicate event IDs are ignored.
            int inserted = jdbcTemplate.update("""
                    INSERT INTO processed_webhook_events
                        (event_id, event_type)
                    VALUES (?, ?)
                    ON CONFLICT (event_id) DO NOTHING
                    """, eventId, eventType);

            if (inserted == 0) {
                return;
            }

            String newStatus = eventType.equals("payment.captured")
                    ? "CAPTURED"
                    : "FAILED";

            int updated = jdbcTemplate.update("""
                    UPDATE payments
                    SET status = ?,
                        razorpay_payment_id = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE razorpay_order_id = ?
                      AND amount_paise = ?
                      AND currency = ?
                      AND (
                          status <> 'CAPTURED'
                          OR ? = 'CAPTURED'
                      )
                    """,
                    newStatus,
                    razorpayPaymentId,
                    razorpayOrderId,
                    amount,
                    currency,
                    newStatus);

            System.out.println("Webhook event: " + eventType);
            System.out.println("Razorpay order ID: " + razorpayOrderId);
            System.out.println("Rows updated: " + updated);

            if (updated != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Payment not found or payment details/status do not match");
            }

            if ("CAPTURED".equals(newStatus)) {

                // Only a CREATED order can transition to PAID here.
                int orderUpdated = jdbcTemplate.update("""
                        UPDATE orders
                        SET status = 'PAID'
                        WHERE id = (
                            SELECT order_id
                            FROM payments
                            WHERE razorpay_order_id = ?
                        )
                        AND status = 'CREATED'
                        """, razorpayOrderId);

                if (orderUpdated == 1) {

                    // Load the order details for the confirmation event.
                    Long orderId = jdbcTemplate.queryForObject("""
                            SELECT order_id
                            FROM payments
                            WHERE razorpay_order_id = ?
                            """,
                            Long.class,
                            razorpayOrderId);

                    if (orderId == null) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Order ID not found for captured payment");
                    }

                    Order order = orderRepository.findById(orderId)
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "Order not found: " + orderId));

                    OrderCreatedEvent event = new OrderCreatedEvent(
                            UUID.randomUUID().toString(),
                            order.getId(),
                            order.getTotalAmount(),
                            LocalDateTime.now(),
                            order.getCustomerEmail()
                    );

                    // Enqueue confirmation only after the order becomes PAID.
                    outboxEventService.recordPaymentCapturedEvent(event);

                } else {
                    // Includes cancelled orders and orders already transitioned.
                    System.out.println(
                            "Payment captured, but order was not CREATED. "
                                    + "No new confirmation event was recorded. "
                                    + "Razorpay order: " + razorpayOrderId);
                }
            }

            jdbcTemplate.update("""
                    UPDATE processed_webhook_events
                    SET processed_at = CURRENT_TIMESTAMP
                    WHERE event_id = ?
                    """, eventId);

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid webhook payload",
                    ex);
        }
    }
}
