package com.example.flashreserve.repository;

import com.example.flashreserve.entity.Order;
import com.example.flashreserve.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Long orderId);

    @Query("""
            SELECT o.id
            FROM Order o
            WHERE o.status = :status
              AND o.createdAt < :cutoff
            ORDER BY o.createdAt ASC
            """)
    List<Long> findExpiredOrderIds(
            @Param("status") OrderStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}