package com.example.flashreserve.repository;


import com.example.flashreserve.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.UUID;


public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {


    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAscIdAsc();
    boolean existsByEventId(UUID eventId);
}
