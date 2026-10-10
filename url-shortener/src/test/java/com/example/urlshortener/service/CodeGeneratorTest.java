package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.config.AppProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator =
            new CodeGenerator(new AppProperties("http://localhost:8080", 7, 5, 30, List.of()));

    @Test
    void generatesCodesOfConfiguredLengthUsingBase62Alphabet() {
        for (int i = 0; i < 1_000; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(7);
            assertThat(code.chars()).allMatch(c -> CodeGenerator.ALPHABET.indexOf(c) >= 0);
        }
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.generate());
        }
        // 62^7 keyspace: a collision in 10k draws is astronomically unlikely
        assertThat(codes).hasSize(10_000);
    }
}
