package dev.jagadeesh.booking.common;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // SKIP LOCKED
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);

    List<OutboxEvent> findByEventTypeOrderByCreatedAtAsc(String eventType);
}
