package com.example.urlshortener.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.rate-limit-per-minute=2")
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static final String BODY = "{\"url\":\"https://example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns429AfterLimitAndDoesNotLimitOtherEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        // Read paths are not rate limited
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
