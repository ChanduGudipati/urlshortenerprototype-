package com.example.urlshortener.web.dto;

import java.time.Instant;

public record UrlStatsResponse(
        String code,
        String originalUrl,
        long totalClicks,
        Instant createdAt,
        Instant expiresAt,
        boolean expired,
        Instant lastClickedAt) {
}
