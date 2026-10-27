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
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
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


    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.order-events}") String topic, OutboxEventStatusService outboxEventStatusService) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.outboxEventStatusService = outboxEventStatusService;
    }


    @Scheduled(fixedDelayString =
            "${app.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository
                        .findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();


        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(
                                topic,
                                event.getAggregateId(),
                                event.getPayload())
                        .get(10, TimeUnit.SECONDS);


//                markPublished(event.getId());
                outboxEventStatusService.markPublished(event.getId());


                log.info(
                        "Published outbox event {} for aggregate {}",
                        event.getEventId(),
                        event.getAggregateId()
                );


            } catch (Exception exception) {
//                recordFailure(event.getId(), exception);
                outboxEventStatusService.recordFailure(event.getId(), exception);

                log.error(
                        "Failed to publish outbox event {}",
                        event.getEventId(),
                        exception
                );
            }
        }
    }


//    @Transactional
//    public void markPublished(Long id) {
//        OutboxEvent event = outboxEventRepository.findById(id)
//                .orElseThrow();
//
//
//        event.markPublished(LocalDateTime.now());
//    }


//    @Transactional
//    public void recordFailure(Long id, Exception exception) {
//        OutboxEvent event = outboxEventRepository.findById(id)
//                .orElseThrow();
//
//
//        String message = exception.getMessage();
//
//
//        if (message == null || message.isBlank()) {
//            message = exception.getClass().getSimpleName();
//        }
//
//
//        // Avoid an unbounded error message in the database.
//        if (message.length() > 2000) {
//            message = message.substring(0, 2000);
//        }
//
//
//        event.recordFailure(message);
//    }
}
