package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AssistantRateLimiterTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-000000000001");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-000000000002");

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T02:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToLimitWithinWindowThenBlocks() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(new MovableClock(), 3, 300);

        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isFalse();
        assertThat(limiter.tryAcquire(OTHER)).isTrue();
    }

    @Test
    void windowSlidesSoOldMessagesStopCounting() {
        MovableClock clock = new MovableClock();
        AssistantRateLimiter limiter = new AssistantRateLimiter(clock, 2, 300);
        limiter.tryAcquire(USER);
        clock.now = clock.now.plus(Duration.ofSeconds(200));
        limiter.tryAcquire(USER);
        assertThat(limiter.tryAcquire(USER)).isFalse();

        clock.now = clock.now.plus(Duration.ofSeconds(101));

        assertThat(limiter.tryAcquire(USER)).isTrue();
    }
}
