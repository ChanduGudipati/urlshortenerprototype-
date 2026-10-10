package com.example.urlshortener.domain;

import java.time.Instant;

/** Minimal immutable view of a short URL, safe to keep in the redirect cache. */
public record RedirectTarget(Long id, String originalUrl, Instant expiresAt) {

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
