package com.example.aikb.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RestClientConfigTests {

    private final RestClientConfig config = new RestClientConfig();

    @Test
    void shouldAttachInternalApiKeyWhenConfigured() {
        RestClient.Builder builder = mock(RestClient.Builder.class);

        config.applyInternalApiKey(builder, "internal-test-key");

        verify(builder).defaultHeader("X-API-Key", "internal-test-key");
    }

    @Test
    void shouldNotAttachInternalApiKeyWhenBlank() {
        RestClient.Builder builder = mock(RestClient.Builder.class);

        config.applyInternalApiKey(builder, " ");

        verify(builder, never()).defaultHeader("X-API-Key", " ");
    }
}
