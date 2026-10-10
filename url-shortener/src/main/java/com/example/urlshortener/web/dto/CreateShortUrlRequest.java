package com.example.urlshortener.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$",
                message = "customAlias must be 4-32 characters: letters, digits, '-' or '_'")
        String customAlias,

        @Positive(message = "expiresInDays must be positive")
        @Max(value = 3650, message = "expiresInDays must be at most 3650")
        Integer expiresInDays) {
}
