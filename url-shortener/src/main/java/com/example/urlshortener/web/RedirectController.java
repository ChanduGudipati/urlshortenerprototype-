package com.example.urlshortener.web;

import com.example.urlshortener.domain.RedirectTarget;
import com.example.urlshortener.exception.ShortUrlExpiredException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.service.AnalyticsService;
import com.example.urlshortener.service.UrlShortenerService;
import java.net.URI;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redirect endpoint. Uses 302 (not 301): browsers cache 301 permanently, which would
 * bypass the service and break click analytics and expiry.
 */
@RestController
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlShortenerService service;
    private final AnalyticsService analyticsService;
    private final Clock clock;

    public RedirectController(UrlShortenerService service, AnalyticsService analyticsService, Clock clock) {
        this.service = service;
        this.analyticsService = analyticsService;
        this.clock = clock;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(
            @PathVariable String code,
            @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {

        if (code.length() < 4 || code.length() > 32) {
            throw new ShortUrlNotFoundException("Short URL '" + code + "' not found");
        }

        RedirectTarget target = service.resolve(code);
        if (target.isExpiredAt(clock.instant())) {
            throw new ShortUrlExpiredException("Short URL '" + code + "' has expired");
        }

        try {
            analyticsService.recordClick(target.id(), referrer, userAgent);
        } catch (TaskRejectedException e) {
            // Analytics is best-effort: a saturated queue must never fail a redirect.
            log.warn("Analytics queue full; click for '{}' not recorded", code);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target.originalUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
