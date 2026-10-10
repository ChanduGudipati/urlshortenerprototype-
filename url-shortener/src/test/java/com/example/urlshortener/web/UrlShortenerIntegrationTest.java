package com.example.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.domain.ShortUrl;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end through HTTP, service, cache, Flyway schema and database (H2). */
@SpringBootTest(properties = "app.rate-limit-per-minute=1000")
@AutoConfigureMockMvc
class UrlShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Test
    void createRedirectAndCountClicks() throws Exception {
        String body = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/docs\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost:8080/")))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/docs"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        String code = json.get("code").asText();
        assertThat(code).hasSize(7);

        mockMvc.perform(get("/" + code).header("User-Agent", "integration-test"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"))
                .andExpect(header().string("Cache-Control", "no-store"));

        // Analytics is asynchronous, so wait for it rather than sleeping.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/urls/" + code + "/stats"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalClicks").value(1))
                        .andExpect(jsonPath("$.lastClickedAt").isNotEmpty()));
    }

    @Test
    void customAliasIsUsedAndDuplicateReturnsConflict() throws Exception {
        String request = "{\"url\":\"https://example.com\",\"customAlias\":\"team-docs\"}";

        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("team-docs"));

        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Alias Unavailable"));
    }

    @Test
    void rejectsUnsafeUrlWithProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid URL"));
    }

    @Test
    void rejectsInvalidRequestFields() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\",\"customAlias\":\"a\",\"expiresInDays\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.customAlias").exists())
                .andExpect(jsonPath("$.errors.expiresInDays").exists());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"));
    }

    @Test
    void unknownCodeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/zzzzzzz")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/urls/zzzzzzz/stats")).andExpect(status().isNotFound());
    }

    @Test
    void expiredLinkReturnsGone() throws Exception {
        Instant now = Instant.now();
        shortUrlRepository.saveAndFlush(new ShortUrl(
                "expired1", "https://example.com", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1))));

        mockMvc.perform(get("/expired1")).andExpect(status().isGone());
        mockMvc.perform(get("/api/v1/urls/expired1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
