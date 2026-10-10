package com.example.urlshortener.exception;

/** Thrown when a submitted URL fails validation. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
