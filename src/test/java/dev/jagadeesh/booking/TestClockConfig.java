package dev.jagadeesh.booking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class TestClockConfig {
    /** Monday 7 Jan 2030, 05:30 in India. */
    public static final Instant START = Instant.parse("2030-01-07T00:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }
}
