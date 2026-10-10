package com.example.urlshortener.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from the "app" prefix. Defaults are applied here so
 * the service starts safely even when a property is missing.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        int codeLength,
        int maxGenerationAttempts,
        int rateLimitPerMinute,
        List<String> blockedDomains) {

    public AppProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (codeLength < 6) {
            codeLength = 7;
        }
        if (maxGenerationAttempts < 1) {
            maxGenerationAttempts = 5;
        }
        blockedDomains = blockedDomains == null ? List.of() : List.copyOf(blockedDomains);
    }
}
