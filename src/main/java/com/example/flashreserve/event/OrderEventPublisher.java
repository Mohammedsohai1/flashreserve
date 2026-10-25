package com.example.flashreserve.event;


//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;



@Component
public class OrderEventPublisher {


    private static final Logger log =
            LoggerFactory.getLogger(OrderEventPublisher.class);


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;


    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.order-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }


    public void publishAfterCommit(OrderCreatedEvent event) {
        final String payload;


        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Could not serialize OrderCreatedEvent", e);
        }


        Runnable publish = () ->
                kafkaTemplate.send(topic, event.orderId().toString(), payload)
                        .whenComplete((result, exception) -> {
                            if (exception != null) {
                                log.error(
                                        "Failed to publish OrderCreated event for order {}",
                                        event.orderId(), exception);
                            } else {
                                log.info(
                                        "Published OrderCreated event for order {} to partition {}",
                                        event.orderId(),
                                        result.getRecordMetadata().partition());
                            }
                        });


        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish.run();
                        }
                    });
        } else {
            publish.run();
        }
    }
}
