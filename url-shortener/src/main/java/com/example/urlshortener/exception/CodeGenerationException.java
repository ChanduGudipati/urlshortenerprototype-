package com.example.urlshortener.exception;

/** Thrown when a unique code cannot be generated after the configured attempts. */
public class CodeGenerationException extends RuntimeException {

    public CodeGenerationException(String message) {
        super(message);
    }
}
