package com.example.flashreserve.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "outbox_events")
public class OutboxEvent {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;


    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;


    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;


    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;


    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    @Column(name = "published_at")
    private LocalDateTime publishedAt;


    @Column(nullable = false)
    private int attempts;


    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;


    protected OutboxEvent() {
    }


    public OutboxEvent(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            LocalDateTime createdAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.attempts = 0;
    }


    public Long getId() {
        return id;
    }


    public UUID getEventId() {
        return eventId;
    }


    public String getAggregateType() {
        return aggregateType;
    }


    public String getAggregateId() {
        return aggregateId;
    }


    public String getEventType() {
        return eventType;
    }


    public String getPayload() {
        return payload;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }


    public int getAttempts() {
        return attempts;
    }


    public String getLastError() {
        return lastError;
    }


    public void markPublished(LocalDateTime time) {
        this.publishedAt = time;
        this.lastError = null;
    }


    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error;
    }
}
