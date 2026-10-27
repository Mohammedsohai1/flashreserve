package com.example.flashreserve.event;


import com.example.flashreserve.service.OrderEventProcessingService;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;


@Component
public class OrderCreatedEventConsumer {


    private final OrderEventProcessingService processingService;


    public OrderCreatedEventConsumer(
            OrderEventProcessingService processingService) {
        this.processingService = processingService;
    }


    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) throws JacksonException {
        processingService.process(payload);
    }
}