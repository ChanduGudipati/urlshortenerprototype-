package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.exception.InvalidUrlException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator(
            new AppProperties("https://sho.rt", 7, 5, 30, List.of("malware.example")));

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?q=1#frag",
            "HTTPS://Example.com/Case",
            "  https://example.com/trimmed  "
    })
    void acceptsValidHttpUrls(String url) {
        assertThat(validator.validate(url).getHost()).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlank(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "ftp://example.com/file",
            "file:///etc/passwd",
            "example.com"
    })
    void rejectsDangerousOrMissingSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> validator.validate("http://exa mple.com"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsOverlongUrl() {
        String url = "https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH);
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void rejectsLinksToThisServiceToPreventRedirectLoops() {
        assertThatThrownBy(() -> validator.validate("https://sho.rt/abc1234"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("this service");
    }

    @Test
    void rejectsBlockedDomainAndItsSubdomains() {
        assertThatThrownBy(() -> validator.validate("https://malware.example/x"))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://cdn.malware.example/x"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void doesNotBlockLookalikeDomains() {
        assertThat(validator.validate("https://notmalware.example/x")).isNotNull();
    }
}
