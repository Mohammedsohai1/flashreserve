package com.example.flashreserve.repository;

import com.example.flashreserve.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKey,Long> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
    @Modifying
    @Query(value = """
    INSERT INTO idempotency_key_locks (idempotency_key)
    VALUES (:idempotencyKey)
    ON CONFLICT (idempotency_key) DO NOTHING
    """, nativeQuery = true)
    void insertLockIfAbsent(
            @Param("idempotencyKey") String idempotencyKey
    );

    @Query(value = """
    SELECT idempotency_key
    FROM idempotency_key_locks
    WHERE idempotency_key = :idempotencyKey
    FOR UPDATE
    """, nativeQuery = true)
    String lockByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey
    );
}
