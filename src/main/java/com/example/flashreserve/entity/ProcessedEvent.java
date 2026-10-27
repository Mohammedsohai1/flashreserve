package com.example.flashreserve.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "processed_events")
public class ProcessedEvent {


    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;


    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;


    protected ProcessedEvent() {
    }


    public ProcessedEvent(UUID eventId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }


    public UUID getEventId() {
        return eventId;
    }


    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
