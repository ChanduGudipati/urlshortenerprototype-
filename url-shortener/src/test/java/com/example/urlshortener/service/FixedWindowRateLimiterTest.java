package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    @Test
    void allowsUpToLimitThenBlocks() {
        FixedWindowRateLimiter limiter = limiter(3, new MutableClock());

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void tracksClientsIndependently() {
        FixedWindowRateLimiter limiter = limiter(1, new MutableClock());

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void resetsInNextWindowAndEvictsStaleEntries() {
        MutableClock clock = new MutableClock();
        FixedWindowRateLimiter limiter = limiter(1, clock);

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();

        clock.advance(Duration.ofMinutes(1));
        limiter.evictExpiredWindows();
        assertThat(limiter.trackedClients()).isZero();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }

    @Test
    void nonPositiveLimitDisablesLimiting() {
        FixedWindowRateLimiter limiter = limiter(0, new MutableClock());
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        }
    }

    private static FixedWindowRateLimiter limiter(int limit, Clock clock) {
        return new FixedWindowRateLimiter(
                new AppProperties("http://localhost:8080", 7, 5, limit, List.of()), clock);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
