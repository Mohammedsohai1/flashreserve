
package com.example.flashreserve.service;

import com.example.flashreserve.dto.VerifyPaymentRequest;
import com.example.flashreserve.entity.Order;
import com.example.flashreserve.entity.OrderStatus;
import com.example.flashreserve.entity.Payment;
import com.example.flashreserve.entity.PaymentStatus;
import com.example.flashreserve.repository.OrderRepository;
import com.example.flashreserve.repository.PaymentRepository;
import com.razorpay.PaymentClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String KEY_ID = "test_key_id";
    private static final String KEY_SECRET = "test_key_secret";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PaymentClient razorpayPaymentClient;

    private RazorpayClient razorpayClient;
    private PaymentService paymentService;

    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        razorpayClient = new RazorpayClient(KEY_ID, KEY_SECRET);

        ReflectionTestUtils.setField(
                razorpayClient, "payments", razorpayPaymentClient);

        paymentService = new PaymentService(
                razorpayClient,
                orderRepository,
                paymentRepository,
                jdbcTemplate,
                KEY_ID,
                KEY_SECRET

        );

        order = new Order(
                OrderStatus.CREATED,
                new BigDecimal("250.00"),
                LocalDateTime.now()
        );

        ReflectionTestUtils.setField(order, "id", 1L);

        payment = new Payment(order, "order_test_123", 25000L);
        payment.setStatus(PaymentStatus.CREATED);
        ReflectionTestUtils.setField(payment, "id", 10L);
    }

    @Test
    void shouldMarkPaymentCapturedAndOrderPaid() throws Exception {
        String razorpayPaymentId = "pay_test_123";

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                10L,
                "order_test_123",
                razorpayPaymentId,
                generateSignature("order_test_123|" + razorpayPaymentId)
        );

        JSONObject fetchedPayment = new JSONObject();
        fetchedPayment.put("id", razorpayPaymentId);
        fetchedPayment.put("order_id", "order_test_123");
        fetchedPayment.put("amount", 25000);
        fetchedPayment.put("currency", "INR");
        fetchedPayment.put("status", "captured");

        com.razorpay.Payment razorpayPayment =
                new com.razorpay.Payment(fetchedPayment);

        when(paymentRepository.findById(10L))
                .thenReturn(Optional.of(payment));

        when(razorpayPaymentClient.fetch(razorpayPaymentId))
                .thenReturn(razorpayPayment);

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(jdbcTemplate.update(
                contains("UPDATE orders"),
                eq(1L)
        )).thenReturn(1);

        var response = paymentService.verifyPayment(request);

        assertNotNull(response);
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        assertEquals(razorpayPaymentId, payment.getRazorpayPaymentId());

        verify(paymentRepository).save(payment);

        verify(jdbcTemplate).update(
                contains("UPDATE orders"),
                eq(1L)
        );
    }

    private String generateSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                KEY_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));

        return HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void shouldReuseExistingCreatedPayment() {
        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(1L))
                .thenReturn(java.util.List.of(payment));

//        when(paymentRepository.save(any(Payment.class)))
//                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.createPayment(1L);

        assertNotNull(response);
        assertEquals(10L, response.paymentId());
        assertEquals(1L, response.orderId());
        assertEquals("order_test_123", response.razorpayOrderId());
        assertEquals(25000L, response.amountPaise());
        assertEquals("INR", response.currency());

        verify(paymentRepository, never()).save(any(Payment.class));
        verifyNoInteractions(razorpayPaymentClient);
    }

}

