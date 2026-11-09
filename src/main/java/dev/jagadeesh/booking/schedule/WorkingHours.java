package dev.jagadeesh.booking.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** A weekly opening window in the business's local time. Several per day are allowed (e.g. 9-13 and 16-20). */
@Entity
@Table(name = "working_hours")
public class WorkingHours {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkingHours() {
    }

    public WorkingHours(UUID providerId, DayOfWeek day, LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End must be after start");
        }
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.dayOfWeek = day.getValue();
        this.startTime = start;
        this.endTime = end;
    }

    public DayOfWeek day() { return DayOfWeek.of(dayOfWeek); }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public UUID getProviderId() { return providerId; }
}
