
package com.example.flashreserve.controller;

import com.example.flashreserve.entity.Order;
import com.example.flashreserve.entity.OrderStatus;
import com.example.flashreserve.entity.Payment;
import com.example.flashreserve.entity.PaymentStatus;
import com.example.flashreserve.repository.OrderRepository;
import com.example.flashreserve.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "razorpay.webhook-secret=test-webhook-secret"
})
@AutoConfigureMockMvc
@Transactional
class RazorpayWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";

    private static final String BODY =
            "{\"event\":\"payment.captured\","
                    + "\"payload\":{\"payment\":{\"entity\":{"
                    + "\"id\":\"pay_test_123\","
                    + "\"order_id\":\"order_test_123\","
                    + "\"amount\":25000,"
                    + "\"currency\":\"INR\"}}}}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    private Long paymentId;

    private String generateSignature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));

        byte[] hash = mac.doFinal(
                body.getBytes(StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(hash);
    }

    @BeforeEach
    void setUp() {
        Order order = new Order(
                OrderStatus.values()[0],
                new BigDecimal("250.00"),
                LocalDateTime.now());

        order = orderRepository.saveAndFlush(order);

        Payment payment = new Payment(
                order,
                "order_test_123",
                25000L);

        payment = paymentRepository.saveAndFlush(payment);
        paymentId = payment.getId();
    }

    @Test
    void shouldProcessCapturedWebhook() throws Exception {
        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature",
                                generateSignature(BODY))
                        .header("X-Razorpay-Event-Id",
                                "evt_test_captured_001")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().string("Webhook processed"));

        // Clear cached JPA entities so we read the database's current state.
        entityManager.flush();
        entityManager.clear();


        Payment updated = paymentRepository.findById(paymentId)
                .orElseThrow();


        assertEquals(PaymentStatus.CAPTURED, updated.getStatus());
        assertEquals("pay_test_123", updated.getRazorpayPaymentId());


        Order updatedOrder = orderRepository.findById(
                updated.getOrder().getId()).orElseThrow();


        assertEquals(OrderStatus.PAID, updatedOrder.getStatus());
    }

    @Test
    void shouldRejectInvalidWebhookSignature() throws Exception {
        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "invalid")
                        .header("X-Razorpay-Event-Id",
                                "evt_test_invalid_001")
                        .content(BODY))
                .andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();

        Payment unchanged = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.CREATED, unchanged.getStatus());
    }
    @Test
    void shouldNotDowngradeCapturedPaymentToFailed() throws Exception {
        // First, process a captured payment.
        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature",
                                generateSignature(BODY))
                        .header("X-Razorpay-Event-Id",
                                "evt_test_captured_first")
                        .content(BODY))
                .andExpect(status().isOk());

        // Then send a failure event for the same Razorpay order.
        String failedBody = """
            {
              "event": "payment.failed",
              "payload": {
                "payment": {
                  "entity": {
                    "id": "pay_test_123",
                    "order_id": "order_test_123",
                    "amount": 25000,
                    "currency": "INR"
                  }
                }
              }
            }
            """;

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature",
                                generateSignature(failedBody))
                        .header("X-Razorpay-Event-Id",
                                "evt_test_failed_after_capture")
                        .content(failedBody))
                .andExpect(status().isConflict());

        entityManager.clear();

        Payment updated = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.CAPTURED, updated.getStatus());
    }

    @Test
    void shouldIgnoreDuplicateWebhookEvent() throws Exception {
        String eventId = "evt_test_duplicate_001";
        String signature = generateSignature(BODY);

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(BODY))
                .andExpect(status().isOk());

        entityManager.clear();

        Payment updated = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertEquals(PaymentStatus.CAPTURED, updated.getStatus());
        assertEquals("pay_test_123", updated.getRazorpayPaymentId());
    }

    @Test
    void shouldNotChangeCancelledOrderToPaid() throws Exception {
        Order order = orderRepository.findById(
                        paymentRepository.findById(paymentId)
                                .orElseThrow()
                                .getOrder().getId())
                .orElseThrow();

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature",
                                generateSignature(BODY))
                        .header("X-Razorpay-Event-Id",
                                "evt_test_cancelled_order_001")
                        .content(BODY))
                .andExpect(status().isOk());

        entityManager.clear();

        Order updatedOrder = orderRepository.findById(order.getId())
                .orElseThrow();

        assertEquals(OrderStatus.CANCELLED, updatedOrder.getStatus());
    }

}
