package com.example.urlshortener.web;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.web.dto.CreateShortUrlRequest;
import com.example.urlshortener.web.dto.ShortUrlResponse;
import com.example.urlshortener.web.dto.UrlStatsResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService service;

    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrl created = service.create(request);
        String shortLink = service.toShortLink(created.getCode());
        ShortUrlResponse body = new ShortUrlResponse(
                created.getCode(),
                shortLink,
                created.getOriginalUrl(),
                created.getCreatedAt(),
                created.getExpiresAt());
        return ResponseEntity.created(URI.create(shortLink)).body(body);
    }

    @GetMapping("/{code}/stats")
    public UrlStatsResponse stats(@PathVariable String code) {
        return service.getStats(code);
    }
}
