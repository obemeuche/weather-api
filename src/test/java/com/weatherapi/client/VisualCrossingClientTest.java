package com.weatherapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherapi.exception.WeatherApiException;
import com.weatherapi.model.WeatherResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisualCrossingClientTest {

    private MockWebServer mockWebServer;
    private VisualCrossingClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        client = new VisualCrossingClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
    }

    @AfterEach
    void tearDown() {
        try {
            mockWebServer.shutdown();
        } catch (IOException ignored) {}
    }

    @Test
    void fetchWeather_200_returnsDeserializedResponse() throws Exception {
        WeatherResponse expected = new WeatherResponse();
        expected.setResolvedAddress("London, England, United Kingdom");
        expected.setTimezone("Europe/London");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(expected)));

        WeatherResponse result = client.fetchWeather("London");

        assertThat(result.getResolvedAddress()).isEqualTo("London, England, United Kingdom");
        assertThat(result.getTimezone()).isEqualTo("Europe/London");
    }

    @Test
    void fetchWeather_401_throwsWithInvalidKeyMessage() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("Unauthorized"));

        assertThatThrownBy(() -> client.fetchWeather("London"))
                .isInstanceOfSatisfying(WeatherApiException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Invalid or missing Weather API key");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void fetchWeather_404_throwsWithCityNotFoundMessage() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("Not Found"));

        assertThatThrownBy(() -> client.fetchWeather("UnknownCity"))
                .isInstanceOfSatisfying(WeatherApiException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("City not found or invalid request: UnknownCity");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void fetchWeather_5xx_throwsWithBadGatewayStatus() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("Internal Server Error"));

        assertThatThrownBy(() -> client.fetchWeather("London"))
                .isInstanceOfSatisfying(WeatherApiException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Weather data provider is temporarily unavailable. Please try again later.");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }

    @Test
    void fetchWeather_networkFailure_throwsWithServiceUnavailableStatus() throws IOException {
        mockWebServer.shutdown();

        assertThatThrownBy(() -> client.fetchWeather("London"))
                .isInstanceOfSatisfying(WeatherApiException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Unable to reach weather data provider. Please try again later.");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchWeather_webClientResponseException_throwsWithBadGatewayStatus() {
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec mockUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec mockHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);

        when(mockWebClient.get()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(any(Function.class))).thenReturn(mockHeadersSpec);
        when(mockHeadersSpec.retrieve()).thenReturn(mockResponseSpec);
        when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
        when(mockResponseSpec.bodyToMono(WeatherResponse.class))
                .thenReturn(Mono.error(new WebClientResponseException(502, "Bad Gateway", null, null, null)));

        VisualCrossingClient clientWithMock = new VisualCrossingClient(mockWebClient);
        ReflectionTestUtils.setField(clientWithMock, "apiKey", "test-api-key");

        assertThatThrownBy(() -> clientWithMock.fetchWeather("London"))
                .isInstanceOfSatisfying(WeatherApiException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Failed to fetch weather data for city: London");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                });
    }
}
