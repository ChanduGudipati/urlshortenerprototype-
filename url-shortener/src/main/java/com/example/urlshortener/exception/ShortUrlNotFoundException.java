package com.example.urlshortener.exception;

/** Thrown when no short URL exists for a code. */
public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String message) {
        super(message);
    }
}
