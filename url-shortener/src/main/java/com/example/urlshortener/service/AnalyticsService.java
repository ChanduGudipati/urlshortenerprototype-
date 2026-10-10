package com.example.urlshortener.service;

import com.example.urlshortener.domain.ClickEvent;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.ShortUrlRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records clicks off the request thread so analytics never adds latency to, or
 * breaks, a redirect. Trade-off: clicks queued in memory are lost if the process
 * crashes (acceptable for analytics; see ENGINEERING_SUMMARY.md).
 * Failures surface through Spring's async uncaught-exception handler (logged).
 */
@Service
public class AnalyticsService {

    static final int MAX_HEADER_LENGTH = 512;

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;

    public AnalyticsService(ShortUrlRepository shortUrlRepository,
                            ClickEventRepository clickEventRepository,
                            Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
    }

    @Async
    @Transactional
    public void recordClick(Long shortUrlId, String referrer, String userAgent) {
        shortUrlRepository.incrementClickCount(shortUrlId);
        clickEventRepository.save(new ClickEvent(
                shortUrlId, clock.instant(), truncate(referrer), truncate(userAgent)));
    }

    static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_HEADER_LENGTH ? value : value.substring(0, MAX_HEADER_LENGTH);
    }
}
