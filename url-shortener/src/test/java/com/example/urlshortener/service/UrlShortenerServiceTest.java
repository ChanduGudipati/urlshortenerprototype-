package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.exception.AliasUnavailableException;
import com.example.urlshortener.exception.CodeGenerationException;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.web.dto.CreateShortUrlRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UrlShortenerServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private ShortUrlRepository shortUrlRepository;
    private ClickEventRepository clickEventRepository;
    private CodeGenerator codeGenerator;
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        shortUrlRepository = mock(ShortUrlRepository.class);
        clickEventRepository = mock(ClickEventRepository.class);
        codeGenerator = mock(CodeGenerator.class);
        AppProperties props = new AppProperties("http://localhost:8080", 7, 3, 30, List.of());
        service = new UrlShortenerService(
                shortUrlRepository, clickEventRepository, codeGenerator,
                new UrlValidator(props), props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsShortUrlWithGeneratedCode() {
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(shortUrlRepository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(new CreateShortUrlRequest("https://example.com", null, null));

        assertThat(result.getCode()).isEqualTo("abc1234");
        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    void computesExpiryFromExpiresInDays() {
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(shortUrlRepository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(new CreateShortUrlRequest("https://example.com", null, 7));

        assertThat(result.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void retriesOnCodeCollision() {
        when(codeGenerator.generate()).thenReturn("dupe123", "fresh12");
        when(shortUrlRepository.saveAndFlush(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(new CreateShortUrlRequest("https://example.com", null, null));

        assertThat(result.getCode()).isEqualTo("fresh12");
        verify(shortUrlRepository, times(2)).saveAndFlush(any(ShortUrl.class));
    }

    @Test
    void failsAfterMaxCollisionAttempts() {
        when(codeGenerator.generate()).thenReturn("dupe123");
        when(shortUrlRepository.saveAndFlush(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", null, null)))
                .isInstanceOf(CodeGenerationException.class);
        verify(shortUrlRepository, times(3)).saveAndFlush(any(ShortUrl.class));
    }

    @Test
    void usesCustomAliasWithoutGeneratingCode() {
        when(shortUrlRepository.saveAndFlush(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.create(new CreateShortUrlRequest("https://example.com", "my-link", null));

        assertThat(result.getCode()).isEqualTo("my-link");
        verify(codeGenerator, never()).generate();
    }

    @Test
    void rejectsTakenAlias() {
        when(shortUrlRepository.saveAndFlush(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", "taken", null)))
                .isInstanceOf(AliasUnavailableException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void rejectsReservedAliasCaseInsensitively() {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", "Actuator", null)))
                .isInstanceOf(AliasUnavailableException.class)
                .hasMessageContaining("reserved");
        verify(shortUrlRepository, never()).saveAndFlush(any(ShortUrl.class));
    }

    @Test
    void doesNotPersistInvalidUrl() {
        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("javascript:alert(1)", null, null)))
                .isInstanceOf(InvalidUrlException.class);
        verify(shortUrlRepository, never()).saveAndFlush(any(ShortUrl.class));
    }

    @Test
    void resolveThrowsWhenCodeUnknown() {
        when(shortUrlRepository.findByCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing")).isInstanceOf(ShortUrlNotFoundException.class);
    }
}
