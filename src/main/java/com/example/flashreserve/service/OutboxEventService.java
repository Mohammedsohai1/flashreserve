package com.example.flashreserve.service;


import com.example.flashreserve.entity.OutboxEvent;
import com.example.flashreserve.event.OrderCreatedEvent;
import com.example.flashreserve.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


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
}
