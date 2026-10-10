package com.example.urlshortener.web.dto;

import java.time.Instant;

public record ShortUrlResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt) {
}
