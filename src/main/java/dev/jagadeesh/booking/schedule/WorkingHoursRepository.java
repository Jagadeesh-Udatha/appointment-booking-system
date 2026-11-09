package dev.jagadeesh.booking.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {
    List<WorkingHours> findByProviderIdAndDayOfWeekOrderByStartTime(UUID providerId, int dayOfWeek);

    @Modifying
    @Query("delete from WorkingHours w where w.providerId = :providerId")
    void deleteByProviderId(UUID providerId);
}
