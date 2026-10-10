package com.example.urlshortener.exception;

/** Thrown when a custom alias is taken or reserved. */
public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String message) {
        super(message);
    }
}
