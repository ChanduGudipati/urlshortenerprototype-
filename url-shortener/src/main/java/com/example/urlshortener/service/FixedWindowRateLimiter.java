package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-client fixed-window limiter (requests per minute). In-memory, so limits are
 * per instance; a multi-instance deployment would move this to Redis
 * (documented trade-off). A limit of 0 or less disables limiting.
 */
@Component
public class FixedWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int limitPerWindow;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(AppProperties properties, Clock clock) {
        this.limitPerWindow = properties.rateLimitPerMinute();
        this.clock = clock;
    }

    public boolean tryAcquire(String clientKey) {
        if (limitPerWindow <= 0) {
            return true;
        }
        long currentWindow = clock.millis() / WINDOW_MILLIS;
        Window window = windows.compute(clientKey, (key, existing) ->
                existing == null || existing.index != currentWindow ? new Window(currentWindow) : existing);
        return window.count.incrementAndGet() <= limitPerWindow;
    }

    /** Evicts finished windows so memory stays bounded by active clients. */
    @Scheduled(fixedRate = WINDOW_MILLIS)
    public void evictExpiredWindows() {
        long currentWindow = clock.millis() / WINDOW_MILLIS;
        windows.entrySet().removeIf(e -> e.getValue().index < currentWindow);
    }

    int trackedClients() {
        return windows.size();
    }

    private static final class Window {
        private final long index;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long index) {
            this.index = index;
        }
    }
}
