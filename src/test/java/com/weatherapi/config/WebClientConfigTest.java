package com.weatherapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebClientConfigTest {

    @InjectMocks
    private WebClientConfig config;

    @Test
    void visualCrossingWebClient_returnsConfiguredWebClient() {
        WebClient webClient = config.visualCrossingWebClient(
                WebClient.builder(),
                "https://weather.visualcrossing.com"
        );

        assertThat(webClient).isNotNull();
    }
}
