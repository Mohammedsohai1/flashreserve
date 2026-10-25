package com.example.flashreserve.event;


//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;


@Component
public class OrderCreatedEventConsumer {


    private static final Logger log =
            LoggerFactory.getLogger(OrderCreatedEventConsumer.class);


    private final ObjectMapper objectMapper;


    public OrderCreatedEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String payload) throws JacksonException {
        OrderCreatedEvent event =
                objectMapper.readValue(payload, OrderCreatedEvent.class);


        log.info(
                "Consumed OrderCreated event: eventId={}, orderId={}, totalAmount={}",
                event.eventId(),
                event.orderId(),
                event.totalAmount());
    }
}
