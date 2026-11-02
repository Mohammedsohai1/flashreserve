package com.example.flashreserve.event;

import com.example.flashreserve.service.OrderEventProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderCreatedEventConsumer {

    private final OrderEventProcessingService processingService;
    private final ObjectMapper objectMapper;

    public OrderCreatedEventConsumer(
            OrderEventProcessingService processingService,
            ObjectMapper objectMapper) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) throws JacksonException {
        OutboxKafkaMessage event =
                objectMapper.readValue(message, OutboxKafkaMessage.class);

        processingService.process(
                event.eventType(),
                event.payload());
    }
}