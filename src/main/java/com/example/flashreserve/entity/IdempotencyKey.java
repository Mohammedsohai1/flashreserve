package com.example.flashreserve.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
        }
)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name="request_fingerprint",nullable = false,length=64)
    private String requestFingerprint;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(
            String idempotencyKey,
            String requestFingerprint,
            Order order,
            LocalDateTime createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint=requestFingerprint;
        this.order = order;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    public String getRequestFingerprint(){
        return requestFingerprint;
    }

    public Order getOrder() {
        return order;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}