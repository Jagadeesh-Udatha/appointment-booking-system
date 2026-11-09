package dev.jagadeesh.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modular monolith: one deployable, one database, with packages that only talk through their public services
 * (tenant, catalog, schedule, booking, waitlist, notification). Side effects that can happen later
 * (reminders, emails, waitlist offers) leave through Kafka via the outbox.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
