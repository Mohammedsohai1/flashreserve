package com.example.flashreserve.service;


import com.example.flashreserve.entity.OutboxEvent;
import com.example.flashreserve.event.OrderCreatedEvent;
import com.example.flashreserve.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;


@Service
public class OutboxEventService {


    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;


    public OutboxEventService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }


    @Transactional
    public void recordOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);


            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.fromString(event.eventId()),
                    "Order",
                    event.orderId().toString(),
                    "OrderCreated",
                    payload,
                    LocalDateTime.now()
            );


            outboxEventRepository.save(outboxEvent);


        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize OrderCreatedEvent",
                    exception
            );
        }
    }
    @Transactional
    public void recordPaymentCapturedEvent(OrderCreatedEvent event) {
        try {
            UUID eventId = UUID.nameUUIDFromBytes(
                    ("PaymentCaptured:" + event.orderId())
                            .getBytes(StandardCharsets.UTF_8)
            );
            if (outboxEventRepository.existsByEventId(eventId)) {
                return;
            }

            OrderCreatedEvent paymentCapturedEvent = new OrderCreatedEvent(
                    eventId.toString(),
                    event.orderId(),
                    event.totalAmount(),
                    event.occurredAt(),
                    event.customerEmail()
            );

            String payload =
                    objectMapper.writeValueAsString(paymentCapturedEvent);

            OutboxEvent outboxEvent = new OutboxEvent(
                    eventId,
                    "Order",
                    event.orderId().toString(),
                    "PaymentCaptured",
                    payload,
                    LocalDateTime.now()
            );

            outboxEventRepository.save(outboxEvent);

        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize PaymentCapturedEvent",
                    exception
            );
        }
    }
}
