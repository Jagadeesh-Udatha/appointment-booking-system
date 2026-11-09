package dev.jagadeesh.booking;

import dev.jagadeesh.booking.booking.BookingService;
import dev.jagadeesh.booking.catalog.CatalogService;
import dev.jagadeesh.booking.common.Outbox;
import dev.jagadeesh.booking.notification.NotificationLogRepository;
import dev.jagadeesh.booking.schedule.ScheduleService;
import dev.jagadeesh.booking.tenant.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static dev.jagadeesh.booking.Fixture.TUESDAY;
import static dev.jagadeesh.booking.Fixture.at;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Kafka: confirm a booking, the relay publishes, the listener writes the customer's message. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "booking.outbox.relay-enabled=true",
        "booking.outbox.poll-ms=600000"
})
@EmbeddedKafka(partitions = 1, topics = Outbox.TOPIC)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class OutboxKafkaTest {

    @Autowired BookingService bookings;
    @Autowired AccountService accounts;
    @Autowired CatalogService catalog;
    @Autowired ScheduleService schedule;
    @Autowired Outbox outbox;
    @Autowired NotificationLogRepository logs;
    @Autowired MutableClock clock;

    @Test
    void confirmedBookingReachesTheCustomerThroughKafka() throws Exception {
        clock.set(TestClockConfig.START);
        Fixture clinic = new Fixture(accounts, catalog, schedule);
        var customer = clinic.customer(accounts);
        var held = bookings.hold(customer, clinic.doctor.getId(), clinic.consult.getId(), at(TUESDAY, 9, 30));
        bookings.confirm(customer, held.getId());

        assertThat(outbox.publish()).isGreaterThanOrEqualTo(1);

        long deadline = System.currentTimeMillis() + 15_000;
        while (logs.findTop50ByTenantIdAndCustomerIdOrderBySentAtDesc(clinic.tenant.getId(), customer.userId()).isEmpty()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(logs.findTop50ByTenantIdAndCustomerIdOrderBySentAtDesc(clinic.tenant.getId(), customer.userId()))
                .singleElement().satisfies(n -> assertThat(n.getMessage()).contains("Tue 8 Jan, 9:30 am"));
    }
}
