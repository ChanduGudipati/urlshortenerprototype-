package com.example.urlshortener.web;

import com.example.urlshortener.exception.AliasUnavailableException;
import com.example.urlshortener.exception.CodeGenerationException;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.ShortUrlExpiredException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps errors to RFC 7807 problem responses without leaking internals. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail invalidUrl(InvalidUrlException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid URL", ex.getMessage());
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ProblemDetail notFound(ShortUrlNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(ShortUrlExpiredException.class)
    public ProblemDetail expired(ShortUrlExpiredException ex) {
        return problem(HttpStatus.GONE, "Expired", ex.getMessage());
    }

    @ExceptionHandler(AliasUnavailableException.class)
    public ProblemDetail aliasUnavailable(AliasUnavailableException ex) {
        return problem(HttpStatus.CONFLICT, "Alias Unavailable", ex.getMessage());
    }

    @ExceptionHandler(CodeGenerationException.class)
    public ProblemDetail codeGeneration(CodeGenerationException ex) {
        log.error("Code generation exhausted retries", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation Failed", "Request validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request", "Request body is missing or malformed");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Standard Spring MVC errors (404 for unknown paths, 405, etc.) keep their status.
            return errorResponse.getBody();
        }
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
