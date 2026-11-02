package com.example.flashreserve.event;

public record OutboxKafkaMessage(
        String eventType,
        String payload) {
}
