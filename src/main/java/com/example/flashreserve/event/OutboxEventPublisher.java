package com.example.flashreserve.event;

import com.example.flashreserve.entity.OutboxEvent;
import com.example.flashreserve.repository.OutboxEventRepository;
import com.example.flashreserve.service.OutboxEventStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final OutboxEventStatusService outboxEventStatusService;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.order-events}") String topic,
            OutboxEventStatusService outboxEventStatusService,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.outboxEventStatusService = outboxEventStatusService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository
                        .findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();

        for (OutboxEvent event : pendingEvents) {
            try {
                OutboxKafkaMessage message = new OutboxKafkaMessage(
                        event.getEventType(),
                        event.getPayload());

                String kafkaPayload =
                        objectMapper.writeValueAsString(message);

                kafkaTemplate.send(
                                topic,
                                event.getAggregateId(),
                                kafkaPayload)
                        .get(10, TimeUnit.SECONDS);

                outboxEventStatusService.markPublished(event.getId());

                log.info(
                        "Published outbox event {} of type {} for aggregate {}",
                        event.getEventId(),
                        event.getEventType(),
                        event.getAggregateId());

            } catch (Exception exception) {
                outboxEventStatusService.recordFailure(
                        event.getId(), exception);

                log.error(
                        "Failed to publish outbox event {}",
                        event.getEventId(),
                        exception);
            }
        }
    }
}