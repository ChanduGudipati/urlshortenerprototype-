package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.exception.InvalidUrlException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Security guardrail for user-supplied destinations. Only http/https are allowed
 * (blocks javascript:, data:, file: and similar), a host is required, links back to
 * this service are rejected to prevent redirect loops, and blocked domains
 * (including their subdomains) are refused.
 */
@Component
public class UrlValidator {

    static final int MAX_LENGTH = 2048;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final List<String> blockedDomains;
    private final String ownHost;

    public UrlValidator(AppProperties properties) {
        this.blockedDomains = properties.blockedDomains().stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .toList();
        String host = URI.create(properties.baseUrl()).getHost();
        this.ownHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    public URI validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("URL must be at most " + MAX_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is malformed");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a valid host");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);

        if (normalizedHost.equals(ownHost)) {
            throw new InvalidUrlException("Links to this service cannot be shortened");
        }
        if (isBlocked(normalizedHost)) {
            throw new InvalidUrlException("Destination domain is not allowed");
        }
        return uri;
    }

    private boolean isBlocked(String host) {
        return blockedDomains.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
    }
}
