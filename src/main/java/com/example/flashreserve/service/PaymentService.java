
package com.example.flashreserve.service;

import com.example.flashreserve.dto.CreatePaymentResponse;
import com.example.flashreserve.dto.VerifyPaymentRequest;
import com.example.flashreserve.dto.VerifyPaymentResponse;
import com.example.flashreserve.entity.Order;
import com.example.flashreserve.entity.OrderStatus;
import com.example.flashreserve.entity.Payment;
import com.example.flashreserve.entity.PaymentStatus;
import com.example.flashreserve.repository.OrderRepository;
import com.example.flashreserve.repository.PaymentRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String keyId;
    private final String keySecret;

    public PaymentService(
            RazorpayClient razorpayClient,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret
    ) {
        this.razorpayClient = razorpayClient;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    @Transactional
    public CreatePaymentResponse createPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot pay for a cancelled order");
        }

        if (order.getStatus() == OrderStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is already paid");
        }
        Payment existingPayment = paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .findFirst()
                .orElse(null);


        if (existingPayment != null) {
            return new CreatePaymentResponse(
                    existingPayment.getId(),
                    order.getId(),
                    existingPayment.getRazorpayOrderId(),
                    existingPayment.getAmountPaise(),
                    existingPayment.getCurrency(),
                    keyId
            );
        }
        BigDecimal totalAmount = order.getTotalAmount();

        long amountPaise = totalAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        if (amountPaise <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must be positive");
        }

        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", "INR");
            request.put("receipt", "order_" + order.getId());

            com.razorpay.Order razorpayOrder =
                    razorpayClient.orders.create(request);

            String razorpayOrderId = razorpayOrder.get("id");

            Payment payment = new Payment(
                    order, razorpayOrderId, amountPaise);

            payment.setStatus(PaymentStatus.CREATED);

            Payment savedPayment = paymentRepository.save(payment);

            return new CreatePaymentResponse(
                    savedPayment.getId(),
                    order.getId(),
                    razorpayOrderId,
                    amountPaise,
                    "INR",
                    keyId
            );

        } catch (RazorpayException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to create Razorpay order", e);
        }
    }

    @Transactional
    public VerifyPaymentResponse verifyPayment(
            VerifyPaymentRequest request) {

        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment record not found"));

        if (!Objects.equals(
                payment.getRazorpayOrderId(),
                request.razorpayOrderId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Razorpay order does not match the payment record");
        }

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            markOrderPaidIfCreated(payment);

            return new VerifyPaymentResponse(
                    payment.getId(),
                    payment.getStatus().name(),
                    "Payment is already captured");
        }

        String payload = request.razorpayOrderId()
                + "|" + request.razorpayPaymentId();

        try {
            boolean validSignature = Utils.verifySignature(
                    payload,
                    request.razorpaySignature(),
                    keySecret);

            if (!validSignature) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid Razorpay payment signature");
            }

            // Fetch payment status directly from Razorpay.
            com.razorpay.Payment razorpayPayment =
                    razorpayClient.payments.fetch(
                            request.razorpayPaymentId());

            String fetchedOrderId = razorpayPayment.get("order_id");
            long fetchedAmount =
                    ((Number) razorpayPayment.get("amount")).longValue();
            String fetchedCurrency = razorpayPayment.get("currency");
            String razorpayStatus = razorpayPayment.get("status");

            if (!Objects.equals(
                    payment.getRazorpayOrderId(), fetchedOrderId)
                    || fetchedAmount != payment.getAmountPaise()
                    || !Objects.equals(
                    payment.getCurrency(), fetchedCurrency)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Razorpay payment details do not match our records");
            }

            switch (razorpayStatus) {
                case "captured" ->
                        payment.markCaptured(request.razorpayPaymentId());

                case "authorized" ->
                        payment.markAuthorized(request.razorpayPaymentId());

                case "failed" ->
                        payment.markFailed(
                                "Razorpay reports payment failure");

                default -> {
                    return new VerifyPaymentResponse(
                            payment.getId(),
                            payment.getStatus().name(),
                            "Payment status is still " + razorpayStatus);
                }
            }

            paymentRepository.save(payment);

            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                markOrderPaidIfCreated(payment);
            }

            return new VerifyPaymentResponse(
                    payment.getId(),
                    payment.getStatus().name(),
                    "Payment status confirmed with Razorpay");

        } catch (RazorpayException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not confirm payment status with Razorpay");
        }
    }

    private void markOrderPaidIfCreated(Payment payment) {
        int updated = jdbcTemplate.update("""
            UPDATE orders
            SET status = 'PAID'
            WHERE id = ?
              AND status = 'CREATED'
            """, payment.getOrder().getId());


        if (updated == 0) {
            Order order = orderRepository.findById(
                            payment.getOrder().getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Order not found"));


            if (order.getStatus() != OrderStatus.PAID) {
                System.err.println(
                        "RECONCILIATION REQUIRED: Razorpay payment "
                                + "is captured, but order " + order.getId()
                                + " has status " + order.getStatus());
            }
        }
    }
}
