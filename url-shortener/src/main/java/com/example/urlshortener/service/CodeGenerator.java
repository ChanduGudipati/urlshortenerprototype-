package com.example.urlshortener.service;

import com.example.urlshortener.config.AppProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates random Base62 codes. Random (not sequential) codes are used so short
 * links cannot be enumerated. With 7 characters there are ~3.5 trillion codes;
 * the rare collision is handled by the database unique constraint plus retry.
 */
@Component
public class CodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public CodeGenerator(AppProperties properties) {
        this.length = properties.codeLength();
    }

    public String generate() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
