package com.example.flashreserve.service;




import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


@Service
public class RazorpayWebhookService {


    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;


    public RazorpayWebhookService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
            long amount =
                    paymentNode.path("amount").asLong(-1);


            if (razorpayPaymentId == null
                    || razorpayOrderId == null
                    || currency == null
                    || amount <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Missing payment details in webhook");
            }


            // Record the event first. A duplicate event ID is ignored.
            int inserted = jdbcTemplate.update("""
                    INSERT INTO processed_webhook_events
                        (event_id, event_type)
                    VALUES (?, ?)
                    ON CONFLICT (event_id) DO NOTHING
                    """, eventId, eventType);


            if (inserted == 0) {
                return; // Already processed.
            }


            String newStatus = eventType.equals("payment.captured")
                    ? "CAPTURED" : "FAILED";


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

                if (orderUpdated == 0) {
                    System.out.println(
                            "Payment captured, but order was not CREATED. "
                                    + "Check whether the order was cancelled. Razorpay order: "
                                    + razorpayOrderId);
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
                    "Invalid webhook payload");
        }
    }
}
