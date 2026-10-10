package com.example.urlshortener.exception;

/** Thrown when a short URL exists but has expired. */
public class ShortUrlExpiredException extends RuntimeException {

    public ShortUrlExpiredException(String message) {
        super(message);
    }
}
