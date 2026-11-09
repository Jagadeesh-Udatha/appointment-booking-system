package dev.jagadeesh.booking.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {
    @Query("select t from TimeOff t where t.providerId = :providerId and t.startAt < :to and t.endAt > :from")
    List<TimeOff> overlapping(UUID providerId, Instant from, Instant to);
}
