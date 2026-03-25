package com.weatherapi.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebClientConfigTest {

    @InjectMocks
    private WebClientConfig config;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void visualCrossingWebClient_returnsConfiguredWebClient() {
        WebClient webClient = config.visualCrossingWebClient(
                WebClient.builder(),
                mockWebServer.url("/").toString()
        );

        assertThat(webClient).isNotNull();
    }

    @Test
    void visualCrossingWebClient_triggersDoOnConnectedHandlers() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        WebClient webClient = config.visualCrossingWebClient(
                WebClient.builder(),
                mockWebServer.url("/").toString()
        );

        // Making a real request triggers doOnConnected, executing the read/write timeout handlers
        String response = webClient.get().retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(response).isEqualTo("{}");
    }
}
