package dev.jagadeesh.booking.booking;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select b from Booking b where b.tenantId = :tenantId and b.providerId = :providerId
              and b.startAt >= :from and b.startAt < :to order by b.startAt
            """)
    List<Booking> dayView(UUID tenantId, UUID providerId, Instant from, Instant to);

    List<Booking> findByTenantIdAndCustomerIdOrderByStartAtDesc(UUID tenantId, UUID customerId);

    /** Rows locked FOR UPDATE SKIP LOCKED: several app instances can run the job without fighting over rows. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select b from Booking b where b.status = dev.jagadeesh.booking.booking.BookingStatus.HELD and b.holdExpiresAt < :now")
    List<Booking> expiredHolds(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select b from Booking b where b.status = dev.jagadeesh.booking.booking.BookingStatus.CONFIRMED
              and b.reminderSentAt is null and b.startAt > :now and b.startAt <= :until
            """)
    List<Booking> remindersDue(Instant now, Instant until);
}
