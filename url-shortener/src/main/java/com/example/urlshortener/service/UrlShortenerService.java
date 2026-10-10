package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.domain.RedirectTarget;
import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.exception.AliasUnavailableException;
import com.example.urlshortener.exception.CodeGenerationException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.web.dto.CreateShortUrlRequest;
import com.example.urlshortener.web.dto.UrlStatsResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    /** Aliases that would shadow application paths. */
    static final Set<String> RESERVED_ALIASES =
            Set.of("api", "actuator", "swagger-ui", "v3", "h2-console", "health", "error");

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final CodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AppProperties properties;
    private final Clock clock;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository,
                               ClickEventRepository clickEventRepository,
                               CodeGenerator codeGenerator,
                               UrlValidator urlValidator,
                               AppProperties properties,
                               Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates a short URL. Deliberately NOT @Transactional: each insert attempt runs
     * in its own repository transaction, so a unique-constraint failure on one attempt
     * does not poison the retry (PostgreSQL aborts the whole transaction on error).
     */
    public ShortUrl create(CreateShortUrlRequest request) {
        String destination = urlValidator.validate(request.url()).toString();
        Instant now = clock.instant();
        Instant expiresAt = request.expiresInDays() == null
                ? null
                : now.plus(Duration.ofDays(request.expiresInDays()));

        if (request.customAlias() != null) {
            return createWithAlias(request.customAlias(), destination, now, expiresAt);
        }

        for (int attempt = 1; attempt <= properties.maxGenerationAttempts(); attempt++) {
            String code = codeGenerator.generate();
            try {
                return shortUrlRepository.saveAndFlush(new ShortUrl(code, destination, now, expiresAt));
            } catch (DataIntegrityViolationException e) {
                log.warn("Short code collision on attempt {} of {}", attempt, properties.maxGenerationAttempts());
            }
        }
        throw new CodeGenerationException("Could not generate a unique short code, please retry");
    }

    private ShortUrl createWithAlias(String alias, String destination, Instant now, Instant expiresAt) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new AliasUnavailableException("Alias '" + alias + "' is reserved");
        }
        try {
            return shortUrlRepository.saveAndFlush(new ShortUrl(alias, destination, now, expiresAt));
        } catch (DataIntegrityViolationException e) {
            // Unique constraint is the source of truth; no check-then-insert race.
            throw new AliasUnavailableException("Alias '" + alias + "' is already in use");
        }
    }

    /**
     * Hot path for redirects. Cached because mappings are immutable once created.
     * Expiry is checked by the caller on every request, so a cached entry can never
     * serve an expired link. Not-found results throw and are therefore not cached.
     */
    @Cacheable(cacheNames = "redirects", key = "#code")
    public RedirectTarget resolve(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL '" + code + "' not found"));
        return new RedirectTarget(shortUrl.getId(), shortUrl.getOriginalUrl(), shortUrl.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String code) {
        ShortUrl shortUrl = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL '" + code + "' not found"));
        Instant lastClickedAt = clickEventRepository.findLastClickedAt(shortUrl.getId());
        return new UrlStatsResponse(
                shortUrl.getCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getClickCount(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt(),
                shortUrl.isExpiredAt(clock.instant()),
                lastClickedAt);
    }

    public String toShortLink(String code) {
        return properties.baseUrl() + "/" + code;
    }
}
