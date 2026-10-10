package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    @Test
    void truncatesLongHeaders() {
        String longValue = "x".repeat(AnalyticsService.MAX_HEADER_LENGTH + 100);
        assertThat(AnalyticsService.truncate(longValue)).hasSize(AnalyticsService.MAX_HEADER_LENGTH);
    }

    @Test
    void keepsShortAndNullHeaders() {
        assertThat(AnalyticsService.truncate("Mozilla/5.0")).isEqualTo("Mozilla/5.0");
        assertThat(AnalyticsService.truncate(null)).isNull();
    }
}
