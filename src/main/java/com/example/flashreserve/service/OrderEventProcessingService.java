package com.example.flashreserve.service;


import com.example.flashreserve.event.OrderCreatedEvent;
import com.example.flashreserve.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


import java.util.UUID;


@Service
public class OrderEventProcessingService {


    private static final Logger log =
            LoggerFactory.getLogger(OrderEventProcessingService.class);


    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderNotificationService notificationService;


    public OrderEventProcessingService(
            ObjectMapper objectMapper,
            ProcessedEventRepository processedEventRepository,
            OrderNotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.notificationService = notificationService;
    }


    @Transactional
    public void process(String payload) throws JacksonException {
        OrderCreatedEvent event =
                objectMapper.readValue(payload, OrderCreatedEvent.class);


        UUID eventId = UUID.fromString(event.eventId());


        int inserted =
                processedEventRepository.insertIfNotProcessed(eventId);


        if (inserted == 0) {
            log.info(
                    "Skipping duplicate OrderCreated event: eventId={}",
                    event.eventId());
            return;
        }


        // Send the email before this transaction commits.
        // If sending fails, the transaction rolls back and the event
        // is not permanently recorded as processed.
        notificationService.sendOrderConfirmation(event);


        log.info(
                "Processed OrderCreated event and sent confirmation: eventId={}, orderId={}",
                event.eventId(),
                event.orderId());
    }
}
