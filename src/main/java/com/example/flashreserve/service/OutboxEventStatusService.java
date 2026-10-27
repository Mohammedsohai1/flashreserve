package com.example.flashreserve.service;


import com.example.flashreserve.entity.OutboxEvent;
import com.example.flashreserve.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;


@Service
public class OutboxEventStatusService {


    private final OutboxEventRepository repository;


    public OutboxEventStatusService(
            OutboxEventRepository repository) {
        this.repository = repository;
    }


    @Transactional
    public void markPublished(Long id) {
        OutboxEvent event = repository.findById(id)
                .orElseThrow();
        event.markPublished(LocalDateTime.now());
    }


    @Transactional
    public void recordFailure(Long id, Exception exception) {
        OutboxEvent event = repository.findById(id)
                .orElseThrow();


        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        if (message.length() > 2000) {
            message = message.substring(0, 2000);
        }


        event.recordFailure(message);
    }
}
